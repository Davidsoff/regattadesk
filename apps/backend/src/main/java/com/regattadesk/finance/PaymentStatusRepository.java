package com.regattadesk.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Repository for payment status related database operations.
 * 
 * This class encapsulates all SQL queries and database operations for
 * managing entry and club payment statuses, keeping the service layer
 * focused on business logic.
 */
@ApplicationScoped
public class PaymentStatusRepository {

    private static final int IN_CLAUSE_CHUNK_SIZE = 500;

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Loads a single entry payment row by regatta and entry ID.
     */
    public Optional<EntryPaymentRow> loadEntryPaymentRow(UUID regattaId, UUID entryId) {
        String sql = """
            SELECT
                e.id,
                e.regatta_id,
                e.billing_club_id,
                e.payment_status,
                e.paid_at,
                e.paid_by,
                e.payment_reference,
                c.id AS crew_id,
                c.club_id AS crew_club_id,
                c.is_composite
            FROM entries e
            JOIN crews c ON c.id = e.crew_id
            WHERE e.regatta_id = ? AND e.id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapEntryPaymentRow(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load entry payment row", e);
        }
    }

    /**
     * Loads multiple entry payment rows by regatta and entry IDs.
     * Handles large ID sets by chunking to avoid IN-clause limits.
     */
    public Map<UUID, EntryPaymentRow> loadEntryPaymentRows(UUID regattaId, Set<UUID> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return Map.of();
        }

        if (entryIds.size() > IN_CLAUSE_CHUNK_SIZE) {
            Map<UUID, EntryPaymentRow> result = new HashMap<>();
            List<UUID> idList = new ArrayList<>(entryIds);
            for (int i = 0; i < idList.size(); i += IN_CLAUSE_CHUNK_SIZE) {
                Set<UUID> chunk = new LinkedHashSet<>(
                    idList.subList(i, Math.min(i + IN_CLAUSE_CHUNK_SIZE, idList.size()))
                );
                result.putAll(loadEntryPaymentRowsChunk(regattaId, chunk));
            }
            return result;
        }
        return loadEntryPaymentRowsChunk(regattaId, entryIds);
    }

    private Map<UUID, EntryPaymentRow> loadEntryPaymentRowsChunk(UUID regattaId, Set<UUID> entryIds) {
        StringBuilder placeholders = new StringBuilder();
        int index = 0;
        for (UUID ignored : entryIds) {
            if (index++ > 0) {
                placeholders.append(", ");
            }
            placeholders.append("?");
        }

        String sql = """
            SELECT
                e.id,
                e.regatta_id,
                e.billing_club_id,
                e.payment_status,
                e.paid_at,
                e.paid_by,
                e.payment_reference,
                c.id AS crew_id,
                c.club_id AS crew_club_id,
                c.is_composite
            FROM entries e
            JOIN crews c ON c.id = e.crew_id
            WHERE e.regatta_id = ? AND e.id IN (%s)
            """.formatted(placeholders);

        Map<UUID, EntryPaymentRow> rowsByEntryId = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            int parameterIndex = 2;
            for (UUID entryId : entryIds) {
                stmt.setObject(parameterIndex++, entryId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    EntryPaymentRow row = mapEntryPaymentRow(rs);
                    rowsByEntryId.put(row.entryId(), row);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load entry payment rows", e);
        }

        return rowsByEntryId;
    }

    /**
     * Lists all entry payment rows for a given club in a regatta.
     */
    public List<EntryPaymentRow> listClubEntryPaymentRows(UUID regattaId, UUID clubId) {
        List<EntryPaymentRow> rows = new ArrayList<>();
        String sql = """
            SELECT
                e.id,
                e.regatta_id,
                e.billing_club_id,
                e.payment_status,
                e.paid_at,
                e.paid_by,
                e.payment_reference,
                c.id AS crew_id,
                c.club_id AS crew_club_id,
                c.is_composite
            FROM entries e
            JOIN crews c ON c.id = e.crew_id
            WHERE e.regatta_id = ?
              AND (
                    e.billing_club_id = ?
                 OR (
                        e.billing_club_id IS NULL
                    AND c.is_composite = FALSE
                    AND c.club_id = ?
                 )
              )
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, clubId);
            stmt.setObject(3, clubId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapEntryPaymentRow(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list club entry payments", e);
        }
        return rows;
    }

    /**
     * Loads entry payment status details for a single entry.
     */
    public Optional<EntryPaymentStatusDetails> getEntryPaymentStatus(UUID regattaId, UUID entryId) {
        String sql = """
            SELECT
                e.id,
                e.regatta_id,
                e.billing_club_id,
                e.payment_status,
                e.paid_at,
                e.paid_by,
                e.payment_reference
            FROM entries e
            WHERE e.regatta_id = ? AND e.id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(new EntryPaymentStatusDetails(
                    (UUID) rs.getObject("id"),
                    (UUID) rs.getObject("regatta_id"),
                    (UUID) rs.getObject("billing_club_id"),
                    PaymentStatus.fromValue(rs.getString("payment_status")),
                    toInstant(rs.getTimestamp("paid_at")),
                    rs.getString("paid_by"),
                    rs.getString("payment_reference")
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load entry payment status", e);
        }
    }

    /**
     * Loads club payment status details.
     */
    public Optional<ClubPaymentStatusDetails> getClubPaymentStatus(UUID regattaId, UUID clubId) {
        String sql = """
            SELECT regatta_id, club_id, payment_status, billable_entry_count, paid_entry_count
            FROM club_payment_statuses
            WHERE regatta_id = ? AND club_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, clubId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.of(new ClubPaymentStatusDetails(
                        regattaId,
                        clubId,
                        PaymentStatus.UNPAID,
                        0,
                        0
                    ));
                }

                return Optional.of(new ClubPaymentStatusDetails(
                    (UUID) rs.getObject("regatta_id"),
                    (UUID) rs.getObject("club_id"),
                    PaymentStatus.fromValue(rs.getString("payment_status")),
                    rs.getInt("billable_entry_count"),
                    rs.getInt("paid_entry_count")
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load club payment status", e);
        }
    }

    /**
     * Checks if a club exists in the database.
     */
    public boolean clubExists(UUID clubId) {
        String sql = "SELECT id FROM clubs WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, clubId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify club existence", e);
        }
    }

    /**
     * Finds a previous bulk operation result for idempotency checking (PostgreSQL optimized).
     */
    public Optional<BulkPaymentStatusMarkedEvent> findBulkOperationReplayPostgres(
        UUID regattaId,
        String actor,
        String idempotencyKey,
        PaymentStatus targetStatus,
        String requestFingerprint
    ) {
        String sql = """
            SELECT payload
            FROM event_store
            WHERE aggregate_id = ?
              AND event_type = 'BulkPaymentStatusMarked'
              AND payload ->> 'idempotencyKey' = ?
              AND payload ->> 'requestedBy' = ?
              AND payload ->> 'targetStatus' = ?
              AND payload ->> 'requestFingerprint' = ?
            ORDER BY created_at DESC
            LIMIT 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setString(2, idempotencyKey);
            stmt.setString(3, actor);
            stmt.setString(4, targetStatus.value());
            stmt.setString(5, requestFingerprint);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                BulkPaymentStatusMarkedEvent event = objectMapper.readValue(
                    rs.getString("payload"),
                    BulkPaymentStatusMarkedEvent.class
                );
                return Optional.of(event);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve idempotency replay", e);
        }
    }

    /**
     * Finds a previous bulk operation result using fallback query (for non-PostgreSQL).
     */
    public Optional<BulkPaymentStatusMarkedEvent> findBulkOperationReplayFallback(
        UUID regattaId,
        String actor,
        String idempotencyKey,
        PaymentStatus targetStatus,
        String requestFingerprint
    ) {
        String sql = """
            SELECT payload
            FROM event_store
            WHERE aggregate_id = ?
              AND event_type = 'BulkPaymentStatusMarked'
            ORDER BY created_at DESC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    BulkPaymentStatusMarkedEvent event = objectMapper.readValue(
                        rs.getString("payload"),
                        BulkPaymentStatusMarkedEvent.class
                    );
                    if (idempotencyKey.equals(event.getIdempotencyKey())
                        && actor.equals(event.getRequestedBy())
                        && targetStatus.value().equals(event.getTargetStatus())
                        && requestFingerprint.equals(event.getRequestFingerprint())) {
                        return Optional.of(event);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve idempotency replay", e);
        }
        return Optional.empty();
    }

    private EntryPaymentRow mapEntryPaymentRow(ResultSet rs) throws Exception {
        UUID billingClubId = (UUID) rs.getObject("billing_club_id");
        UUID crewClubId = (UUID) rs.getObject("crew_club_id");
        boolean isComposite = rs.getBoolean("is_composite");

        UUID effectiveClubId = billingClubId != null
            ? billingClubId
            : (!isComposite ? crewClubId : null);

        return new EntryPaymentRow(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("regatta_id"),
            PaymentStatus.fromValue(rs.getString("payment_status")),
            toInstant(rs.getTimestamp("paid_at")),
            rs.getString("paid_by"),
            rs.getString("payment_reference"),
            effectiveClubId
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record EntryPaymentRow(
        UUID entryId,
        UUID regattaId,
        PaymentStatus paymentStatus,
        Instant paidAt,
        String paidBy,
        String paymentReference,
        UUID effectiveClubId
    ) {
    }

    /**
     * Recomputes club payment status via projection handler.
     */
    public void recomputeClubPaymentStatus(UUID regattaId, UUID clubId, FinanceProjectionHandler projectionHandler) {
        try (Connection conn = dataSource.getConnection()) {
            projectionHandler.recomputeClubPaymentStatus(conn, regattaId, clubId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh club payment status projection", e);
        }
    }

    /**
     * Checks if the database is PostgreSQL.
     */
    public boolean isPostgres() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        } catch (Exception e) {
            return false;
        }
    }
}
