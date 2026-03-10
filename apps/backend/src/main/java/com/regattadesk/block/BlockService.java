package com.regattadesk.block;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.eventstore.DomainEvent;
import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.eventstore.EventMetadata;
import com.regattadesk.eventstore.EventStore;
import com.regattadesk.regatta.RegattaWorkflowService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class BlockService {

    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;
    private final RegattaWorkflowService regattaWorkflowService;

    @Inject
    public BlockService(
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

    public List<BlockView> listBlocks(UUID regattaId) {
        return listBlocksFromEvents(regattaId);
    }

    public BlockView createBlock(UUID regattaId, String name, Instant startTime, int eventIntervalSeconds, int crewIntervalSeconds) {
        ensureRegattaEditable(regattaId);
        int displayOrder = listBlocksFromEvents(regattaId).size() + 1;
        BlockAggregate block = BlockAggregate.create(
            UUID.randomUUID(),
            regattaId,
            name,
            startTime,
            eventIntervalSeconds,
            crewIntervalSeconds,
            displayOrder
        );
        saveAggregate(block);
        return toView(block);
    }

    public BlockView updateBlock(UUID regattaId, UUID blockId, String name, Instant startTime, int eventIntervalSeconds, int crewIntervalSeconds) {
        ensureRegattaEditable(regattaId);
        BlockAggregate block = requireAggregate(blockId);
        if (!regattaId.equals(block.getRegattaId())) {
            throw new IllegalArgumentException("Block not found");
        }
        block.update(name, startTime, eventIntervalSeconds, crewIntervalSeconds, block.getDisplayOrder());
        saveAggregate(block);
        return toView(block);
    }

    public void deleteBlock(UUID regattaId, UUID blockId) {
        ensureRegattaEditable(regattaId);
        BlockAggregate block = requireAggregate(blockId);
        if (!regattaId.equals(block.getRegattaId())) {
            throw new IllegalArgumentException("Block not found");
        }
        block.delete();
        saveAggregate(block);
    }

    public List<BlockView> reorderBlocks(UUID regattaId, List<ReorderItem> items) {
        ensureRegattaEditable(regattaId);
        for (ReorderItem item : items) {
            BlockAggregate block = requireAggregate(item.blockId());
            if (!regattaId.equals(block.getRegattaId())) {
                throw new IllegalArgumentException("Block not found");
            }
            block.update(
                block.getName(),
                block.getStartTime(),
                block.getEventIntervalSeconds(),
                block.getCrewIntervalSeconds(),
                item.displayOrder()
            );
            saveAggregate(block);
        }
        return listBlocks(regattaId);
    }

    private void ensureRegattaEditable(UUID regattaId) {
        RegattaWorkflowService.RegattaState regatta = regattaWorkflowService.requireRegatta(regattaId);
        if (regatta.drawPublished()) {
            throw new IllegalStateException("Cannot edit blocks after draw publication");
        }
    }

    private BlockAggregate requireAggregate(UUID blockId) {
        return loadAggregate(blockId)
            .orElseThrow(() -> new IllegalArgumentException("Block not found"));
    }

    private Optional<BlockAggregate> loadAggregate(UUID id) {
        List<EventEnvelope> envelopes = eventStore.readStream(id);
        if (envelopes.isEmpty()) {
            return Optional.empty();
        }

        List<DomainEvent> events = envelopes.stream()
            .map(this::toDomainEvent)
            .toList();

        BlockAggregate block = new BlockAggregate(id);
        block.loadFromHistory(events);
        return Optional.of(block);
    }

    private void saveAggregate(BlockAggregate block) {
        int newEventCount = block.getUncommittedEvents().size();
        long expectedVersion = newEventCount > block.getVersion()
            ? -1
            : block.getVersion() - newEventCount + 1;

        eventStore.append(
            block.getId(),
            block.getAggregateType(),
            expectedVersion,
            block.getUncommittedEvents(),
            EventMetadata.builder().build()
        );

        block.markEventsAsCommitted();
    }

    private DomainEvent toDomainEvent(EventEnvelope envelope) {
        Class<? extends DomainEvent> eventClass = switch (envelope.getEventType()) {
            case "BlockCreated" -> BlockCreatedEvent.class;
            case "BlockUpdated" -> BlockUpdatedEvent.class;
            case "BlockDeleted" -> BlockDeletedEvent.class;
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
            throw new IllegalStateException("Failed to deserialize block event " + envelope.getEventType(), e);
        }
    }

    private BlockView toView(BlockAggregate block) {
        return new BlockView(
            block.getId(),
            block.getRegattaId(),
            block.getName(),
            block.getStartTime(),
            block.getEventIntervalSeconds(),
            block.getCrewIntervalSeconds(),
            block.getDisplayOrder()
        );
    }

    private List<BlockView> listBlocksFromEvents(UUID regattaId) {
        LinkedHashSet<UUID> blockIds = eventStore.readGlobal(10_000, 0).stream()
            .filter(event -> "Block".equals(event.getAggregateType()))
            .map(EventEnvelope::getAggregateId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return blockIds.stream()
            .map(this::loadAggregate)
            .flatMap(Optional::stream)
            .filter(block -> !block.isDeleted() && regattaId.equals(block.getRegattaId()))
            .map(this::toView)
            .sorted(java.util.Comparator.comparingInt(BlockView::displayOrder))
            .toList();
    }

    public record BlockView(
        UUID id,
        UUID regattaId,
        String name,
        Instant startTime,
        int eventIntervalSeconds,
        int crewIntervalSeconds,
        int displayOrder
    ) {
    }

    public record ReorderItem(UUID blockId, int displayOrder) {
    }
}
