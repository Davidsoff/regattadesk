package com.regattadesk.bibpool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.eventstore.DomainEvent;
import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.eventstore.EventMetadata;
import com.regattadesk.eventstore.EventStore;
import com.regattadesk.regatta.RegattaWorkflowService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@ApplicationScoped
public class BibPoolService {

    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;
    private final RegattaWorkflowService regattaWorkflowService;

    @Inject
    public BibPoolService(
        EventStore eventStore,
        ObjectMapper objectMapper,
        DataSource dataSource,
        RegattaWorkflowService regattaWorkflowService
    ) {
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
        this.regattaWorkflowService = regattaWorkflowService;
    }

    public List<BibPoolView> listBibPools(UUID regattaId) {
        return listBibPoolsFromEvents(regattaId);
    }

    public BibPoolView createBibPool(
        UUID regattaId,
        UUID blockId,
        String name,
        String allocationMode,
        Integer startBib,
        Integer endBib,
        List<Integer> bibNumbers,
        Boolean isOverflow
    ) {
        ensureRegattaEditable(regattaId);
        int priority = listBibPoolsFromEvents(regattaId).size() + 1;
        validateOverlap(regattaId, null, allocationMode, startBib, endBib, bibNumbers);

        BibPoolAggregate pool = "explicit_list".equals(allocationMode)
            ? BibPoolAggregate.createWithExplicitList(UUID.randomUUID(), regattaId, blockId, name, bibNumbers, priority, Boolean.TRUE.equals(isOverflow))
            : BibPoolAggregate.createWithRange(UUID.randomUUID(), regattaId, blockId, name, startBib, endBib, priority, Boolean.TRUE.equals(isOverflow));

        saveAggregate(pool);
        return toView(pool);
    }

    public BibPoolView updateBibPool(
        UUID regattaId,
        UUID poolId,
        String name,
        String allocationMode,
        Integer startBib,
        Integer endBib,
        List<Integer> bibNumbers
    ) {
        ensureRegattaEditable(regattaId);
        validateOverlap(regattaId, poolId, allocationMode, startBib, endBib, bibNumbers);
        BibPoolAggregate pool = requireAggregate(poolId);
        if (!regattaId.equals(pool.getRegattaId())) {
            throw new IllegalArgumentException("Bib pool not found");
        }

        if ("explicit_list".equals(allocationMode)) {
            pool.updateExplicitList(name, bibNumbers, pool.getPriority());
        } else {
            pool.updateRange(name, startBib, endBib, pool.getPriority());
        }

        saveAggregate(pool);
        return toView(pool);
    }

    public void deleteBibPool(UUID regattaId, UUID poolId) {
        ensureRegattaEditable(regattaId);
        BibPoolAggregate pool = requireAggregate(poolId);
        if (!regattaId.equals(pool.getRegattaId())) {
            throw new IllegalArgumentException("Bib pool not found");
        }
        pool.delete();
        saveAggregate(pool);
    }

    public List<BibPoolView> reorderBibPools(UUID regattaId, List<ReorderItem> items) {
        ensureRegattaEditable(regattaId);
        for (ReorderItem item : items) {
            BibPoolAggregate pool = requireAggregate(item.poolId());
            if (!regattaId.equals(pool.getRegattaId())) {
                throw new IllegalArgumentException("Bib pool not found");
            }
            if ("explicit_list".equals(pool.getAllocationMode())) {
                pool.updateExplicitList(pool.getName(), pool.getBibNumbers(), item.priority());
            } else {
                pool.updateRange(pool.getName(), pool.getStartBib(), pool.getEndBib(), item.priority());
            }
            saveAggregate(pool);
        }
        return listBibPools(regattaId);
    }

    private void ensureRegattaEditable(UUID regattaId) {
        RegattaWorkflowService.RegattaState regatta = regattaWorkflowService.requireRegatta(regattaId);
        if (regatta.drawPublished()) {
            throw new IllegalStateException("Cannot edit bib pools after draw publication");
        }
    }

    private void validateOverlap(
        UUID regattaId,
        UUID ignorePoolId,
        String allocationMode,
        Integer startBib,
        Integer endBib,
        List<Integer> bibNumbers
    ) {
        SortedSet<Integer> candidate = new TreeSet<>(toNumbers(allocationMode, startBib, endBib, bibNumbers));
        SortedSet<Integer> overlapping = new TreeSet<>();
        String conflictingPoolName = null;
        UUID conflictingPoolId = null;

        for (BibPoolView existing : listBibPoolsFromEvents(regattaId)) {
            if (ignorePoolId != null && ignorePoolId.equals(existing.id())) {
                continue;
            }
            Set<Integer> existingNumbers = toNumbers(
                existing.allocationMode(),
                existing.startBib(),
                existing.endBib(),
                existing.bibNumbers()
            );
            for (Integer bib : candidate) {
                if (existingNumbers.contains(bib)) {
                    overlapping.add(bib);
                }
            }
            if (!overlapping.isEmpty() && conflictingPoolId == null) {
                conflictingPoolId = existing.id();
                conflictingPoolName = existing.name();
            }
        }

        if (!overlapping.isEmpty()) {
            throw new BibPoolValidationException(
                "Bib pool overlap detected",
                new ArrayList<>(overlapping),
                conflictingPoolId,
                conflictingPoolName
            );
        }
    }

    private Set<Integer> toNumbers(String allocationMode, Integer startBib, Integer endBib, List<Integer> bibNumbers) {
        LinkedHashSet<Integer> numbers = new LinkedHashSet<>();
        if ("explicit_list".equals(allocationMode)) {
            if (bibNumbers != null) {
                numbers.addAll(bibNumbers);
            }
            return numbers;
        }
        if (startBib != null && endBib != null) {
            for (int bib = startBib; bib <= endBib; bib++) {
                numbers.add(bib);
            }
        }
        return numbers;
    }

    private BibPoolAggregate requireAggregate(UUID poolId) {
        return loadAggregate(poolId)
            .orElseThrow(() -> new IllegalArgumentException("Bib pool not found"));
    }

    private Optional<BibPoolAggregate> loadAggregate(UUID id) {
        List<EventEnvelope> envelopes = eventStore.readStream(id);
        if (envelopes.isEmpty()) {
            return Optional.empty();
        }

        List<DomainEvent> events = envelopes.stream()
            .map(this::toDomainEvent)
            .toList();

        BibPoolAggregate pool = new BibPoolAggregate(id);
        pool.loadFromHistory(events);
        return Optional.of(pool);
    }

    private void saveAggregate(BibPoolAggregate pool) {
        int newEventCount = pool.getUncommittedEvents().size();
        long expectedVersion = newEventCount > pool.getVersion()
            ? -1
            : pool.getVersion() - newEventCount + 1;

        eventStore.append(
            pool.getId(),
            pool.getAggregateType(),
            expectedVersion,
            pool.getUncommittedEvents(),
            EventMetadata.builder().build()
        );

        pool.markEventsAsCommitted();
    }

    private DomainEvent toDomainEvent(EventEnvelope envelope) {
        Class<? extends DomainEvent> eventClass = switch (envelope.getEventType()) {
            case "BibPoolCreated" -> BibPoolCreatedEvent.class;
            case "BibPoolUpdated" -> BibPoolUpdatedEvent.class;
            case "BibPoolDeleted" -> BibPoolDeletedEvent.class;
            default -> null;
        };

        if (eventClass == null) {
            return envelope.getPayload();
        }

        try {
            String rawPayload = envelope.getRawPayload();
            if (rawPayload == null || rawPayload.isBlank()) {
                return objectMapper.convertValue(envelope.getPayload(), eventClass);
            }
            return objectMapper.readValue(rawPayload, eventClass);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize bib pool event " + envelope.getEventType(), e);
        }
    }

    private BibPoolView toView(BibPoolAggregate pool) {
        return new BibPoolView(
            pool.getId(),
            pool.getRegattaId(),
            pool.getBlockId(),
            pool.getName(),
            pool.getAllocationMode(),
            pool.getStartBib(),
            pool.getEndBib(),
            pool.getBibNumbers(),
            pool.getPriority(),
            pool.isOverflow()
        );
    }

    private List<Integer> readIntArray(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object value = array.getArray();
        if (value instanceof Integer[] ints) {
            return Arrays.asList(ints);
        }
        if (value instanceof Object[] objects) {
            return Arrays.stream(objects).map(v -> (Integer) v).toList();
        }
        return null;
    }

    private List<BibPoolView> listBibPoolsFromEvents(UUID regattaId) {
        String sql = """
            SELECT id, regatta_id, block_id, name, allocation_mode, start_bib, end_bib, bib_numbers, priority, is_overflow
            FROM bib_pools
            WHERE regatta_id = ?
            ORDER BY priority
            """;

        List<BibPoolView> pools = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, regattaId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    pools.add(new BibPoolView(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("regatta_id", UUID.class),
                        resultSet.getObject("block_id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("allocation_mode"),
                        (Integer) resultSet.getObject("start_bib"),
                        (Integer) resultSet.getObject("end_bib"),
                        readIntArray(resultSet.getArray("bib_numbers")),
                        resultSet.getInt("priority"),
                        resultSet.getBoolean("is_overflow")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list bib pools for regatta " + regattaId, e);
        }

        return pools;
    }

    public record BibPoolView(
        UUID id,
        UUID regattaId,
        UUID blockId,
        String name,
        String allocationMode,
        Integer startBib,
        Integer endBib,
        List<Integer> bibNumbers,
        int priority,
        boolean isOverflow
    ) {
    }

    public record ReorderItem(UUID poolId, int priority) {
    }
}
