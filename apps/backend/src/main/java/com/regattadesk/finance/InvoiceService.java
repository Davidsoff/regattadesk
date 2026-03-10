package com.regattadesk.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.eventstore.DomainEvent;
import com.regattadesk.eventstore.EventMetadata;
import com.regattadesk.eventstore.EventStore;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@ApplicationScoped
public class InvoiceService {
    private static final Logger LOG = Logger.getLogger(InvoiceService.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final DateTimeFormatter INVOICE_NUMBER_DATE =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    @Inject
    DataSource dataSource;

    @Inject
    EventStore eventStore;

    @Inject
    PaymentStatusService paymentStatusService;

    @Inject
    ObjectMapper objectMapper;

    private final ExecutorService generationExecutor = Executors.newSingleThreadExecutor(new InvoiceThreadFactory());

    public InvoiceListResult listInvoices(UUID regattaId, String cursor, Integer limit, UUID clubId, InvoiceStatus status) {
        int normalizedLimit = normalizeLimit(limit);
        int offset = parseCursor(cursor);
        List<InvoiceRecord> items = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
            SELECT id, regatta_id, club_id, invoice_number, total_amount, currency, status,
                   generated_at, sent_at, paid_at, paid_by, payment_reference
            FROM invoices
            WHERE regatta_id = ?
            """);

        List<Object> params = new ArrayList<>();
        params.add(regattaId);
        if (clubId != null) {
            sql.append(" AND club_id = ?");
            params.add(clubId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.value());
        }
        sql.append(" ORDER BY generated_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(normalizedLimit + 1);
        params.add(offset);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    items.add(readInvoiceRow(rs, List.of()));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list invoices", e);
        }

        boolean hasMore = items.size() > normalizedLimit;
        if (hasMore) {
            items.remove(items.size() - 1);
        }
        try (Connection conn = dataSource.getConnection()) {
            Map<UUID, List<InvoiceEntryLine>> entriesByInvoiceId = loadInvoiceEntries(conn, items.stream()
                .map(InvoiceRecord::id)
                .toList());
            List<InvoiceRecord> withEntries = items.stream()
                .map(invoice -> new InvoiceRecord(
                    invoice.id(),
                    invoice.regattaId(),
                    invoice.clubId(),
                    invoice.invoiceNumber(),
                    entriesByInvoiceId.getOrDefault(invoice.id(), List.of()),
                    invoice.totalAmount(),
                    invoice.currency(),
                    invoice.status(),
                    invoice.generatedAt(),
                    invoice.sentAt(),
                    invoice.paidAt(),
                    invoice.paidBy(),
                    invoice.paymentReference()
                ))
                .toList();
            return new InvoiceListResult(withEntries, hasMore ? String.valueOf(offset + normalizedLimit) : null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to list invoice entries", e);
        }
    }

    public Optional<InvoiceRecord> getInvoice(UUID regattaId, UUID invoiceId) {
        String invoiceSql = """
            SELECT id, regatta_id, club_id, invoice_number, total_amount, currency, status,
                   generated_at, sent_at, paid_at, paid_by, payment_reference
            FROM invoices
            WHERE regatta_id = ? AND id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(invoiceSql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, invoiceId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                List<InvoiceEntryLine> entries = loadInvoiceEntries(conn, invoiceId);
                return Optional.of(readInvoiceRow(rs, entries));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load invoice", e);
        }
    }

    public InvoiceGenerationJob requestGeneration(
        UUID regattaId,
        List<UUID> requestedClubIds,
        String idempotencyKey,
        String actor
    ) {
        String normalizedActor = normalizeRequiredText(actor, "actor");
        String normalizedIdempotencyKey = normalizeNullableText(idempotencyKey);
        List<UUID> normalizedClubIds = normalizeClubIds(requestedClubIds);
        String requestFingerprint = computeRequestFingerprint(normalizedClubIds);

        if (normalizedIdempotencyKey != null) {
            Optional<InvoiceGenerationJob> replay = findJobByIdempotencyKey(
                regattaId,
                normalizedActor,
                normalizedIdempotencyKey,
                requestFingerprint
            );
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        String clubIdsJson = toJson(normalizedClubIds);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 INSERT INTO invoice_generation_jobs (
                     job_id, regatta_id, status, requested_by, idempotency_key,
                     request_fingerprint, requested_club_ids_json, created_at, updated_at
                 ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                 """)) {
            stmt.setObject(1, jobId);
            stmt.setObject(2, regattaId);
            stmt.setString(3, InvoiceGenerationJobStatus.PENDING.value());
            stmt.setString(4, normalizedActor);
            if (normalizedIdempotencyKey == null) {
                stmt.setNull(5, Types.VARCHAR);
            } else {
                stmt.setString(5, normalizedIdempotencyKey);
            }
            stmt.setString(6, requestFingerprint);
            if (clubIdsJson == null) {
                stmt.setNull(7, Types.CLOB);
            } else {
                stmt.setString(7, clubIdsJson);
            }
            stmt.setTimestamp(8, Timestamp.from(now));
            stmt.setTimestamp(9, Timestamp.from(now));
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create invoice generation job", e);
        }

        appendEvent(jobId, "InvoiceGenerationJob", List.of(
            new InvoiceGenerationRequestedEvent(jobId, regattaId, normalizedActor, normalizedClubIds, normalizedIdempotencyKey, now)
        ), normalizedActor);

        generationExecutor.submit(() -> processGenerationJob(jobId, regattaId, normalizedClubIds, normalizedActor));
        return new InvoiceGenerationJob(jobId, regattaId, InvoiceGenerationJobStatus.PENDING, List.of(), null, now, null);
    }

    public Optional<InvoiceGenerationJob> getJob(UUID regattaId, UUID jobId) {
        String sql = """
            SELECT job_id, regatta_id, status, invoice_ids_json, error_message, created_at, completed_at
            FROM invoice_generation_jobs
            WHERE regatta_id = ? AND job_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new InvoiceGenerationJob(
                    (UUID) rs.getObject("job_id"),
                    (UUID) rs.getObject("regatta_id"),
                    InvoiceGenerationJobStatus.fromValue(rs.getString("status")),
                    readUuidList(rs.getString("invoice_ids_json")),
                    rs.getString("error_message"),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("completed_at"))
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load invoice generation job", e);
        }
    }

    @Transactional
    public InvoiceRecord markPaid(
        UUID regattaId,
        UUID invoiceId,
        String paidBy,
        Instant paidAt,
        String paymentReference,
        String actor
    ) {
        String normalizedPaidBy = normalizeRequiredText(paidBy, "paid_by");
        String normalizedActor = normalizeRequiredText(actor, "actor");
        String normalizedReference = normalizeNullableText(paymentReference);
        Instant effectivePaidAt = paidAt == null ? Instant.now() : paidAt;

        InvoiceRecord invoice = getInvoice(regattaId, invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found"));

        if (invoice.status() == InvoiceStatus.PAID) {
            throw new InvoiceConflictException("Invoice is already paid");
        }
        if (invoice.status() == InvoiceStatus.CANCELLED) {
            throw new InvoiceConflictException("Cancelled invoices cannot be paid");
        }

        for (InvoiceEntryLine line : invoice.entries()) {
            paymentStatusService.updateEntryPaymentStatus(
                regattaId,
                line.entryId(),
                PaymentStatus.PAID,
                normalizedReference,
                normalizedPaidBy
            );
        }

        Instant now = Instant.now();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 UPDATE invoices
                 SET status = ?, paid_at = ?, paid_by = ?, payment_reference = ?, updated_at = ?
                 WHERE regatta_id = ? AND id = ?
                 """)) {
            stmt.setString(1, InvoiceStatus.PAID.value());
            stmt.setTimestamp(2, Timestamp.from(effectivePaidAt));
            stmt.setString(3, normalizedPaidBy);
            if (normalizedReference == null) {
                stmt.setNull(4, Types.VARCHAR);
            } else {
                stmt.setString(4, normalizedReference);
            }
            stmt.setTimestamp(5, Timestamp.from(now));
            stmt.setObject(6, regattaId);
            stmt.setObject(7, invoiceId);
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to mark invoice paid", e);
        }

        appendEvent(invoiceId, "Invoice", List.of(
            new InvoiceMarkedPaidEvent(invoiceId, regattaId, invoice.clubId(), effectivePaidAt, normalizedPaidBy, normalizedReference)
        ), normalizedActor);

        return getInvoice(regattaId, invoiceId)
            .orElseThrow(() -> new IllegalStateException("Invoice disappeared after mark-paid update"));
    }

    @PreDestroy
    void shutdownExecutor() {
        generationExecutor.shutdownNow();
    }

    private void processGenerationJob(UUID jobId, UUID regattaId, List<UUID> requestedClubIds, String actor) {
        try {
            updateJobStatus(jobId, InvoiceGenerationJobStatus.RUNNING, null, null);
            List<CandidateEntry> candidates = loadCandidates(regattaId, requestedClubIds);
            BigDecimal entryFee = loadEntryFee(regattaId);
            String currency = loadCurrency(regattaId);
            List<UUID> invoiceIds = new ArrayList<>();

            candidates.stream()
                .collect(java.util.stream.Collectors.groupingBy(CandidateEntry::clubId))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> {
                    UUID clubId = entry.getKey();
                    List<CandidateEntry> clubEntries = entry.getValue().stream()
                        .sorted(Comparator.comparing(CandidateEntry::entryId))
                        .toList();
                    if (clubEntries.isEmpty()) {
                        return;
                    }
                    UUID invoiceId = createInvoice(regattaId, clubId, clubEntries, entryFee, currency, actor);
                    invoiceIds.add(invoiceId);
                });

            updateJobStatus(jobId, InvoiceGenerationJobStatus.COMPLETED, invoiceIds, null);
        } catch (Exception e) {
            LOG.errorf(e, "Invoice generation job %s failed", jobId);
            updateJobStatus(jobId, InvoiceGenerationJobStatus.FAILED, List.of(), rootCauseMessage(e));
        }
    }

    private UUID createInvoice(
        UUID regattaId,
        UUID clubId,
        List<CandidateEntry> clubEntries,
        BigDecimal entryFee,
        String currency,
        String actor
    ) {
        UUID invoiceId = UUID.randomUUID();
        Instant now = Instant.now();
        BigDecimal totalAmount = entryFee.multiply(BigDecimal.valueOf(clubEntries.size()));
        String invoiceNumber = nextInvoiceNumber(regattaId, invoiceId);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement invoiceStmt = conn.prepareStatement("""
                     INSERT INTO invoices (
                         id, regatta_id, club_id, invoice_number, total_amount, currency,
                         status, generated_at, created_at, updated_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """);
                 PreparedStatement entryStmt = conn.prepareStatement("""
                     INSERT INTO invoice_entries (invoice_id, entry_id, amount, created_at)
                     VALUES (?, ?, ?, ?)
                     """)) {
                invoiceStmt.setObject(1, invoiceId);
                invoiceStmt.setObject(2, regattaId);
                invoiceStmt.setObject(3, clubId);
                invoiceStmt.setString(4, invoiceNumber);
                invoiceStmt.setBigDecimal(5, totalAmount);
                invoiceStmt.setString(6, currency);
                invoiceStmt.setString(7, InvoiceStatus.DRAFT.value());
                invoiceStmt.setTimestamp(8, Timestamp.from(now));
                invoiceStmt.setTimestamp(9, Timestamp.from(now));
                invoiceStmt.setTimestamp(10, Timestamp.from(now));
                invoiceStmt.executeUpdate();

                for (CandidateEntry clubEntry : clubEntries) {
                    entryStmt.setObject(1, invoiceId);
                    entryStmt.setObject(2, clubEntry.entryId());
                    entryStmt.setBigDecimal(3, entryFee);
                    entryStmt.setTimestamp(4, Timestamp.from(now));
                    entryStmt.addBatch();
                }
                entryStmt.executeBatch();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create invoice: " + rootCauseMessage(e), e);
        }

        appendEvent(invoiceId, "Invoice", List.of(
            new InvoiceGeneratedEvent(
                invoiceId,
                regattaId,
                clubId,
                invoiceNumber,
                clubEntries.stream().map(CandidateEntry::entryId).toList(),
                totalAmount,
                currency,
                now
            )
        ), actor);
        return invoiceId;
    }

    private List<CandidateEntry> loadCandidates(UUID regattaId, List<UUID> requestedClubIds) {
        StringBuilder sql = new StringBuilder("""
            SELECT e.id, e.billing_club_id
            FROM entries e
            WHERE e.regatta_id = ?
              AND e.billing_club_id IS NOT NULL
              AND e.payment_status = 'unpaid'
              AND NOT EXISTS (
                  SELECT 1
                  FROM invoice_entries ie
                  JOIN invoices i ON i.id = ie.invoice_id
                  WHERE ie.entry_id = e.id
                    AND i.status <> 'cancelled'
              )
            """);
        List<Object> params = new ArrayList<>();
        params.add(regattaId);
        if (!requestedClubIds.isEmpty()) {
            sql.append(" AND e.billing_club_id IN (");
            appendPlaceholders(sql, requestedClubIds.size());
            sql.append(")");
            params.addAll(requestedClubIds);
        }
        sql.append(" ORDER BY e.billing_club_id, e.id");

        List<CandidateEntry> entries = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(new CandidateEntry(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("billing_club_id")
                    ));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load invoice candidates", e);
        }
        return entries;
    }

    private BigDecimal loadEntryFee(UUID regattaId) {
        return loadRegattaValue(regattaId, "entry_fee", rs -> rs.getBigDecimal("entry_fee"));
    }

    private String loadCurrency(UUID regattaId) {
        return loadRegattaValue(regattaId, "currency", rs -> rs.getString("currency"));
    }

    private <T> T loadRegattaValue(UUID regattaId, String column, ResultReader<T> reader) {
        String sql = "SELECT " + column + " FROM regattas WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Regatta not found");
                }
                return reader.read(rs);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load regatta " + column, e);
        }
    }

    private String nextInvoiceNumber(UUID regattaId, UUID invoiceId) {
        String sql = "SELECT COUNT(*) AS c FROM invoices WHERE regatta_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                long next = rs.getLong("c") + 1;
                return "INV-"
                    + INVOICE_NUMBER_DATE.format(Instant.now())
                    + "-"
                    + regattaId.toString().substring(0, 8).toUpperCase(Locale.ROOT)
                    + "-"
                    + String.format(Locale.ROOT, "%04d", next)
                    + "-"
                    + invoiceId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to allocate invoice number", e);
        }
    }

    private void updateJobStatus(UUID jobId, InvoiceGenerationJobStatus status, List<UUID> invoiceIds, String errorMessage) {
        Instant now = Instant.now();
        boolean terminal = status == InvoiceGenerationJobStatus.COMPLETED || status == InvoiceGenerationJobStatus.FAILED;
        String sql = """
            UPDATE invoice_generation_jobs
            SET status = ?, invoice_ids_json = ?, error_message = ?, completed_at = ?, updated_at = ?
            WHERE job_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.value());
            String invoiceIdsJson = invoiceIds == null ? null : toJson(invoiceIds);
            if (invoiceIdsJson == null) {
                stmt.setNull(2, Types.CLOB);
            } else {
                stmt.setString(2, invoiceIdsJson);
            }
            if (errorMessage == null || errorMessage.isBlank()) {
                stmt.setNull(3, Types.CLOB);
            } else {
                stmt.setString(3, errorMessage);
            }
            if (terminal) {
                stmt.setTimestamp(4, Timestamp.from(now));
            } else {
                stmt.setNull(4, Types.TIMESTAMP);
            }
            stmt.setTimestamp(5, Timestamp.from(now));
            stmt.setObject(6, jobId);
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update invoice generation job", e);
        }
    }

    private Optional<InvoiceGenerationJob> findJobByIdempotencyKey(
        UUID regattaId,
        String actor,
        String idempotencyKey,
        String requestFingerprint
    ) {
        String sql = """
            SELECT job_id
            FROM invoice_generation_jobs
            WHERE regatta_id = ?
              AND requested_by = ?
              AND idempotency_key = ?
              AND request_fingerprint = ?
            ORDER BY created_at DESC
            LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setString(2, actor);
            stmt.setString(3, idempotencyKey);
            stmt.setString(4, requestFingerprint);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return getJob(regattaId, (UUID) rs.getObject("job_id"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve invoice generation idempotency", e);
        }
    }

    private String computeRequestFingerprint(List<UUID> requestedClubIds) {
        String payload = requestedClubIds.stream()
            .map(UUID::toString)
            .sorted()
            .reduce((left, right) -> left + "," + right)
            .orElse("");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute invoice request fingerprint", e);
        }
    }

    private List<InvoiceEntryLine> loadInvoiceEntries(Connection conn, UUID invoiceId) throws Exception {
        List<InvoiceEntryLine> entries = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement("""
                 SELECT entry_id, amount
                 FROM invoice_entries
                 WHERE invoice_id = ?
                 ORDER BY entry_id
                 """)) {
            stmt.setObject(1, invoiceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(new InvoiceEntryLine(
                        (UUID) rs.getObject("entry_id"),
                        rs.getBigDecimal("amount")
                    ));
                }
            }
        }
        return entries;
    }

    private Map<UUID, List<InvoiceEntryLine>> loadInvoiceEntries(Connection conn, List<UUID> invoiceIds) throws Exception {
        Map<UUID, List<InvoiceEntryLine>> entriesByInvoiceId = new HashMap<>();
        if (invoiceIds.isEmpty()) {
            return entriesByInvoiceId;
        }

        StringBuilder sql = new StringBuilder("""
            SELECT invoice_id, entry_id, amount
            FROM invoice_entries
            WHERE invoice_id IN (
            """);
        appendPlaceholders(sql, invoiceIds.size());
        sql.append(") ORDER BY invoice_id, entry_id");

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            bindParams(stmt, new ArrayList<>(invoiceIds));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID invoiceId = (UUID) rs.getObject("invoice_id");
                    entriesByInvoiceId.computeIfAbsent(invoiceId, ignored -> new ArrayList<>())
                        .add(new InvoiceEntryLine(
                            (UUID) rs.getObject("entry_id"),
                            rs.getBigDecimal("amount")
                        ));
                }
            }
        }
        return entriesByInvoiceId;
    }

    private InvoiceRecord readInvoiceRow(ResultSet rs, List<InvoiceEntryLine> entries) throws Exception {
        return new InvoiceRecord(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("regatta_id"),
            (UUID) rs.getObject("club_id"),
            rs.getString("invoice_number"),
            entries,
            rs.getBigDecimal("total_amount"),
            rs.getString("currency"),
            InvoiceStatus.fromValue(rs.getString("status")),
            toInstant(rs.getTimestamp("generated_at")),
            toInstant(rs.getTimestamp("sent_at")),
            toInstant(rs.getTimestamp("paid_at")),
            rs.getString("paid_by"),
            rs.getString("payment_reference")
        );
    }

    private void appendEvent(UUID aggregateId, String aggregateType, List<? extends DomainEvent> events, String actor) {
        long expectedVersion = eventStore.getCurrentVersion(aggregateId);
        EventMetadata metadata = EventMetadata.builder()
            .correlationId(UUID.randomUUID())
            .addData("actor", actor)
            .build();
        eventStore.append(aggregateId, aggregateType, expectedVersion, List.copyOf(events), metadata);
    }

    private void bindParams(PreparedStatement stmt, List<Object> params) throws Exception {
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
        }
    }

    private void appendPlaceholders(StringBuilder sql, int count) {
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor.trim()), 0);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("cursor must be a numeric offset");
        }
    }

    private List<UUID> normalizeClubIds(List<UUID> requestedClubIds) {
        if (requestedClubIds == null || requestedClubIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> unique = new LinkedHashSet<>();
        for (UUID requestedClubId : requestedClubIds) {
            if (requestedClubId != null) {
                unique.add(requestedClubId);
            }
        }
        return new ArrayList<>(unique);
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toJson(List<UUID> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize invoice job payload", e);
        }
    }

    private List<UUID> readUuidList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            UUID[] values = objectMapper.readValue(json, UUID[].class);
            return List.of(values);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize UUID list", e);
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    public record InvoiceListResult(
        List<InvoiceRecord> invoices,
        String nextCursor
    ) {
    }

    public static class InvoiceNotFoundException extends RuntimeException {
        public InvoiceNotFoundException(String message) {
            super(message);
        }
    }

    public static class InvoiceConflictException extends RuntimeException {
        public InvoiceConflictException(String message) {
            super(message);
        }
    }

    private record CandidateEntry(
        UUID entryId,
        UUID clubId
    ) {
    }

    @FunctionalInterface
    private interface ResultReader<T> {
        T read(ResultSet rs) throws Exception;
    }

    private static final class InvoiceThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "invoice-generation-worker");
            thread.setDaemon(true);
            return thread;
        }
    }
}
