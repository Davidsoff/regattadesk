package com.regattadesk.adjudication;

import com.regattadesk.adjudication.api.AdjudicationActionRequest;
import com.regattadesk.adjudication.api.AdjudicationEntryDetailResponse;
import com.regattadesk.adjudication.api.OpenInvestigationRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AdjudicationService {

    @Inject
    DataSource dataSource;

    public Optional<AdjudicationEntryDetailResponse> getEntryDetail(UUID regattaId, UUID entryId) {
        try (Connection conn = dataSource.getConnection()) {
            EntryState entry = fetchEntry(conn, regattaId, entryId);
            if (entry == null) {
                return Optional.empty();
            }

            int revision = getResultsRevision(conn, regattaId);
            return Optional.of(new AdjudicationEntryDetailResponse(
                new AdjudicationEntryDetailResponse.EntrySummary(
                    entry.entryId(),
                    entry.crewName(),
                    entry.status(),
                    entry.resultLabel(),
                    entry.penaltySeconds()
                ),
                fetchInvestigations(conn, regattaId, entryId),
                fetchHistory(conn, regattaId, entryId),
                revisionImpact(revision, revision, "No adjudication change has been applied yet.")
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load adjudication detail", e);
        }
    }

    public List<AdjudicationEntryDetailResponse.InvestigationSummary> listInvestigations(UUID regattaId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                SELECT i.id, i.entry_id, c.display_name, i.status, i.description, i.outcome, i.penalty_seconds, i.created_at, i.closed_at
                FROM adjudication_investigations i
                JOIN entries e ON e.id = i.entry_id
                JOIN crews c ON c.id = e.crew_id
                WHERE i.regatta_id = ?
                ORDER BY i.created_at DESC
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, regattaId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AdjudicationEntryDetailResponse.InvestigationSummary> investigations = new ArrayList<>();
                    while (rs.next()) {
                        investigations.add(mapInvestigation(rs));
                    }
                    return investigations;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list investigations", e);
        }
    }

    @Transactional
    public AdjudicationEntryDetailResponse openInvestigation(UUID regattaId, OpenInvestigationRequest request, String actor) {
        validateOpenInvestigation(request);
        try (Connection conn = dataSource.getConnection()) {
            EntryState entry = fetchEntry(conn, regattaId, request.entryId());
            if (entry == null) {
                throw new IllegalArgumentException("Entry not found");
            }

            Instant now = Instant.now();
            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO adjudication_investigations
                    (id, regatta_id, entry_id, status, description, opened_by, created_at, updated_at)
                VALUES (?, ?, ?, 'open', ?, ?, ?, ?)
                """)) {
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, regattaId);
                stmt.setObject(3, request.entryId());
                stmt.setString(4, request.description().trim());
                stmt.setString(5, actor);
                stmt.setTimestamp(6, Timestamp.from(now));
                stmt.setTimestamp(7, Timestamp.from(now));
                stmt.executeUpdate();
            }

            return getEntryDetail(regattaId, request.entryId()).orElseThrow();
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to open investigation", e);
        }
    }

    @Transactional
    public AdjudicationEntryDetailResponse applyPenalty(UUID regattaId, UUID entryId, AdjudicationActionRequest request, String actor) {
        validateAction(request, true);
        return applyAction(regattaId, entryId, "penalty", request, actor);
    }

    @Transactional
    public AdjudicationEntryDetailResponse applyDsq(UUID regattaId, UUID entryId, AdjudicationActionRequest request, String actor) {
        validateAction(request, false);
        return applyAction(regattaId, entryId, "dsq", request, actor);
    }

    @Transactional
    public AdjudicationEntryDetailResponse applyExclusion(UUID regattaId, UUID entryId, AdjudicationActionRequest request, String actor) {
        validateAction(request, false);
        return applyAction(regattaId, entryId, "excluded", request, actor);
    }

    @Transactional
    public AdjudicationEntryDetailResponse revertDsq(UUID regattaId, UUID entryId, AdjudicationActionRequest request, String actor) {
        validateAction(request, false);
        try (Connection conn = dataSource.getConnection()) {
            EntryState current = requireEntry(conn, regattaId, entryId);
            if (!"dsq".equals(current.status())) {
                throw new IllegalArgumentException("Entry is not disqualified");
            }
            String restoredStatus = findLastDsqPreviousStatus(conn, regattaId, entryId)
                .orElseThrow(() -> new IllegalArgumentException("No prior DSQ state found"));
            int nextRevision = incrementResultsRevision(conn, regattaId);
            updateEntryState(conn, entryId, restoredStatus, "provisional", null);
            insertHistory(
                conn,
                regattaId,
                entryId,
                "dsq_reverted",
                request.reason().trim(),
                normalizeNote(request.note()),
                actor,
                current.status(),
                restoredStatus,
                current.resultLabel(),
                "provisional",
                null,
                nextRevision
            );
            return buildDetail(conn, regattaId, entryId, nextRevision, "Results revision advanced to %d after DSQ revert.".formatted(nextRevision));
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to revert DSQ", e);
        }
    }

    private AdjudicationEntryDetailResponse applyAction(
        UUID regattaId,
        UUID entryId,
        String action,
        AdjudicationActionRequest request,
        String actor
    ) {
        try (Connection conn = dataSource.getConnection()) {
            EntryState current = requireEntry(conn, regattaId, entryId);
            int nextRevision = incrementResultsRevision(conn, regattaId);

            String nextStatus = current.status();
            String nextResultLabel = "edited";
            Integer penaltySeconds = null;
            String outcome = null;
            String actionName = action;
            String messageSuffix = switch (action) {
                case "penalty" -> {
                    penaltySeconds = request.penaltySeconds();
                    outcome = "penalty";
                    yield "penalty";
                }
                case "dsq" -> {
                    nextStatus = "dsq";
                    outcome = "dsq";
                    yield "DSQ";
                }
                case "excluded" -> {
                    nextStatus = "excluded";
                    actionName = "exclusion";
                    outcome = "excluded";
                    yield "exclusion";
                }
                default -> throw new IllegalArgumentException("Unsupported action: " + action);
            };

            updateEntryState(conn, entryId, nextStatus, nextResultLabel, penaltySeconds);
            closeOpenInvestigations(conn, regattaId, entryId, outcome, penaltySeconds, actor);
            insertHistory(
                conn,
                regattaId,
                entryId,
                actionName,
                request.reason().trim(),
                normalizeNote(request.note()),
                actor,
                current.status(),
                nextStatus,
                current.resultLabel(),
                nextResultLabel,
                penaltySeconds,
                nextRevision
            );

            return buildDetail(conn, regattaId, entryId, nextRevision,
                "Results revision advanced to %d after %s.".formatted(nextRevision, messageSuffix));
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to apply adjudication action", e);
        }
    }

    private void validateOpenInvestigation(OpenInvestigationRequest request) {
        if (request == null || request.entryId() == null) {
            throw new IllegalArgumentException("Entry ID is required");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new IllegalArgumentException("Description is required");
        }
    }

    private void validateAction(AdjudicationActionRequest request, boolean penaltyRequired) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("Reason is required");
        }
        if (penaltyRequired && (request.penaltySeconds() == null || request.penaltySeconds() <= 0)) {
            throw new IllegalArgumentException("Penalty seconds must be greater than zero");
        }
    }

    private AdjudicationEntryDetailResponse buildDetail(Connection conn, UUID regattaId, UUID entryId, int currentRevision, String message) throws Exception {
        EntryState entry = requireEntry(conn, regattaId, entryId);
        return new AdjudicationEntryDetailResponse(
            new AdjudicationEntryDetailResponse.EntrySummary(entry.entryId(), entry.crewName(), entry.status(), entry.resultLabel(), entry.penaltySeconds()),
            fetchInvestigations(conn, regattaId, entryId),
            fetchHistory(conn, regattaId, entryId),
            revisionImpact(currentRevision, currentRevision, message)
        );
    }

    private AdjudicationEntryDetailResponse.RevisionImpact revisionImpact(int currentRevision, int previewBaseRevision, String message) {
        return new AdjudicationEntryDetailResponse.RevisionImpact(currentRevision, previewBaseRevision + 1, message);
    }

    private EntryState requireEntry(Connection conn, UUID regattaId, UUID entryId) throws Exception {
        EntryState entry = fetchEntry(conn, regattaId, entryId);
        if (entry == null) {
            throw new IllegalArgumentException("Entry not found");
        }
        return entry;
    }

    private EntryState fetchEntry(Connection conn, UUID regattaId, UUID entryId) throws Exception {
        String sql = """
            SELECT e.id, e.status, e.result_label, e.penalty_seconds, c.display_name
            FROM entries e
            JOIN crews c ON c.id = e.crew_id
            WHERE e.regatta_id = ? AND e.id = ?
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new EntryState(
                    (UUID) rs.getObject("id"),
                    rs.getString("display_name"),
                    rs.getString("status"),
                    rs.getString("result_label"),
                    rs.getObject("penalty_seconds", Integer.class)
                );
            }
        }
    }

    private List<AdjudicationEntryDetailResponse.InvestigationSummary> fetchInvestigations(Connection conn, UUID regattaId, UUID entryId) throws Exception {
        String sql = """
            SELECT i.id, i.entry_id, c.display_name, i.status, i.description, i.outcome, i.penalty_seconds, i.created_at, i.closed_at
            FROM adjudication_investigations i
            JOIN entries e ON e.id = i.entry_id
            JOIN crews c ON c.id = e.crew_id
            WHERE i.regatta_id = ? AND i.entry_id = ?
            ORDER BY i.created_at DESC
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<AdjudicationEntryDetailResponse.InvestigationSummary> investigations = new ArrayList<>();
                while (rs.next()) {
                    investigations.add(mapInvestigation(rs));
                }
                return investigations;
            }
        }
    }

    private AdjudicationEntryDetailResponse.InvestigationSummary mapInvestigation(ResultSet rs) throws Exception {
        return new AdjudicationEntryDetailResponse.InvestigationSummary(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("entry_id"),
            rs.getString("display_name"),
            rs.getString("status"),
            rs.getString("description"),
            rs.getString("outcome"),
            rs.getObject("penalty_seconds", Integer.class),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("closed_at"))
        );
    }

    private List<AdjudicationEntryDetailResponse.HistoryItem> fetchHistory(Connection conn, UUID regattaId, UUID entryId) throws Exception {
        String sql = """
            SELECT action, reason, note, actor, previous_status, current_status, previous_result_label,
                   current_result_label, penalty_seconds, results_revision, created_at
            FROM adjudication_history
            WHERE regatta_id = ? AND entry_id = ?
            ORDER BY created_at ASC
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<AdjudicationEntryDetailResponse.HistoryItem> history = new ArrayList<>();
                while (rs.next()) {
                    history.add(new AdjudicationEntryDetailResponse.HistoryItem(
                        rs.getString("action"),
                        rs.getString("reason"),
                        rs.getString("note"),
                        rs.getString("actor"),
                        rs.getString("previous_status"),
                        rs.getString("current_status"),
                        rs.getString("previous_result_label"),
                        rs.getString("current_result_label"),
                        rs.getObject("penalty_seconds", Integer.class),
                        rs.getInt("results_revision"),
                        toInstant(rs.getTimestamp("created_at"))
                    ));
                }
                return history;
            }
        }
    }

    private int getResultsRevision(Connection conn, UUID regattaId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT results_revision FROM regattas WHERE id = ?")) {
            stmt.setObject(1, regattaId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Regatta not found");
                }
                return rs.getInt("results_revision");
            }
        }
    }

    private int incrementResultsRevision(Connection conn, UUID regattaId) throws Exception {
        int nextRevision = getResultsRevision(conn, regattaId) + 1;
        try (PreparedStatement stmt = conn.prepareStatement("""
            UPDATE regattas
            SET results_revision = ?, updated_at = ?
            WHERE id = ?
            """)) {
            stmt.setInt(1, nextRevision);
            stmt.setTimestamp(2, Timestamp.from(Instant.now()));
            stmt.setObject(3, regattaId);
            stmt.executeUpdate();
        }
        return nextRevision;
    }

    private void updateEntryState(Connection conn, UUID entryId, String status, String resultLabel, Integer penaltySeconds) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement("""
            UPDATE entries
            SET status = ?, result_label = ?, penalty_seconds = ?, updated_at = ?
            WHERE id = ?
            """)) {
            stmt.setString(1, status);
            stmt.setString(2, resultLabel);
            if (penaltySeconds == null) {
                stmt.setNull(3, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(3, penaltySeconds);
            }
            stmt.setTimestamp(4, Timestamp.from(Instant.now()));
            stmt.setObject(5, entryId);
            stmt.executeUpdate();
        }
    }

    private void closeOpenInvestigations(Connection conn, UUID regattaId, UUID entryId, String outcome, Integer penaltySeconds, String actor) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement("""
            UPDATE adjudication_investigations
            SET status = 'closed', outcome = ?, penalty_seconds = ?, closed_by = ?, closed_at = ?, updated_at = ?
            WHERE regatta_id = ? AND entry_id = ? AND status = 'open'
            """)) {
            if (outcome == null) {
                stmt.setNull(1, java.sql.Types.VARCHAR);
            } else {
                stmt.setString(1, outcome);
            }
            if (penaltySeconds == null) {
                stmt.setNull(2, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(2, penaltySeconds);
            }
            stmt.setString(3, actor);
            Timestamp now = Timestamp.from(Instant.now());
            stmt.setTimestamp(4, now);
            stmt.setTimestamp(5, now);
            stmt.setObject(6, regattaId);
            stmt.setObject(7, entryId);
            stmt.executeUpdate();
        }
    }

    private void insertHistory(
        Connection conn,
        UUID regattaId,
        UUID entryId,
        String action,
        String reason,
        String note,
        String actor,
        String previousStatus,
        String currentStatus,
        String previousResultLabel,
        String currentResultLabel,
        Integer penaltySeconds,
        int resultsRevision
    ) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement("""
            INSERT INTO adjudication_history
                (id, regatta_id, entry_id, action, reason, note, actor, previous_status, current_status,
                 previous_result_label, current_result_label, penalty_seconds, results_revision, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            stmt.setObject(1, UUID.randomUUID());
            stmt.setObject(2, regattaId);
            stmt.setObject(3, entryId);
            stmt.setString(4, action);
            stmt.setString(5, reason);
            stmt.setString(6, note);
            stmt.setString(7, actor);
            stmt.setString(8, previousStatus);
            stmt.setString(9, currentStatus);
            stmt.setString(10, previousResultLabel);
            stmt.setString(11, currentResultLabel);
            if (penaltySeconds == null) {
                stmt.setNull(12, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(12, penaltySeconds);
            }
            stmt.setInt(13, resultsRevision);
            stmt.setTimestamp(14, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
        }
    }

    private Optional<String> findLastDsqPreviousStatus(Connection conn, UUID regattaId, UUID entryId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement("""
            SELECT previous_status
            FROM adjudication_history
            WHERE regatta_id = ? AND entry_id = ? AND action = 'dsq'
            ORDER BY created_at DESC
            LIMIT 1
            """)) {
            stmt.setObject(1, regattaId);
            stmt.setObject(2, entryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString("previous_status"));
            }
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private record EntryState(UUID entryId, String crewName, String status, String resultLabel, Integer penaltySeconds) {
    }
}
