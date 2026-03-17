package com.regattadesk.regatta.api;

import com.regattadesk.entry.EntryDto;
import com.regattadesk.entry.EntryNotFoundException;
import com.regattadesk.entry.EntryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class RegattaSetupService {
    private static final Set<String> WITHDRAW_STATUSES = Set.of("withdrawn_before_draw", "withdrawn_after_draw");

    @Inject
    DataSource dataSource;

    @Inject
    EntryService entryService;

    public EventGroupResponse createEventGroup(UUID regattaId, EventGroupCreateRequest request) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO event_groups (id, regatta_id, name, description, display_order, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?)
                 """)) {
            statement.setObject(1, id);
            statement.setObject(2, regattaId);
            statement.setString(3, request.name());
            statement.setString(4, request.description());
            statement.setInt(5, request.display_order());
            statement.setTimestamp(6, Timestamp.from(now));
            statement.setTimestamp(7, Timestamp.from(now));
            statement.executeUpdate();
            return new EventGroupResponse(id, regattaId, request.name(), request.description(), request.display_order());
        } catch (Exception e) {
            throw mapMutationFailure("create event group", e);
        }
    }

    public EventGroupResponse updateEventGroup(UUID regattaId, UUID eventGroupId, EventGroupCreateRequest request) {
        Instant now = Instant.now();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE event_groups
                 SET name = ?, description = ?, display_order = ?, updated_at = ?
                 WHERE id = ? AND regatta_id = ?
                 """)) {
            statement.setString(1, request.name());
            statement.setString(2, request.description());
            statement.setInt(3, request.display_order());
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setObject(5, eventGroupId);
            statement.setObject(6, regattaId);
            if (statement.executeUpdate() == 0) {
                throw new SetupNotFoundException("Event group not found");
            }
            return new EventGroupResponse(eventGroupId, regattaId, request.name(), request.description(), request.display_order());
        } catch (SetupNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw mapMutationFailure("update event group", e);
        }
    }

    public void deleteEventGroup(UUID regattaId, UUID eventGroupId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 DELETE FROM event_groups
                 WHERE id = ? AND regatta_id = ?
                 """)) {
            statement.setObject(1, eventGroupId);
            statement.setObject(2, regattaId);
            if (statement.executeUpdate() == 0) {
                throw new SetupNotFoundException("Event group not found");
            }
        } catch (SetupNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw mapMutationFailure("delete event group", e);
        }
    }

    public ListResponse<EventGroupResponse> listEventGroups(UUID regattaId, String search) {
        List<EventGroupResponse> data = new ArrayList<>();
        String normalizedSearch = search == null ? null : search.trim().toLowerCase();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT id, regatta_id, name, description, display_order
                 FROM event_groups
                 WHERE regatta_id = ?
                   AND (? IS NULL OR LOWER(name) LIKE ?)
                 ORDER BY display_order, name, id
                 """)) {
            statement.setObject(1, regattaId);
            statement.setString(2, normalizedSearch == null || normalizedSearch.isBlank() ? null : normalizedSearch);
            statement.setString(3, normalizedSearch == null || normalizedSearch.isBlank() ? null : "%" + normalizedSearch + "%");

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    data.add(new EventGroupResponse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("regatta_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("display_order")
                    ));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list event groups", e);
        }

        return new ListResponse<>(data, new Pagination(false, null));
    }

    public EventResponse createEvent(UUID regattaId, EventCreateRequest request) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO events (id, regatta_id, event_group_id, category_id, boat_type_id, name, display_order, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                 """)) {
            statement.setObject(1, id);
            statement.setObject(2, regattaId);
            statement.setObject(3, request.event_group_id());
            statement.setObject(4, request.category_id());
            statement.setObject(5, request.boat_type_id());
            statement.setString(6, request.name());
            statement.setInt(7, request.display_order());
            statement.setTimestamp(8, Timestamp.from(now));
            statement.setTimestamp(9, Timestamp.from(now));
            statement.executeUpdate();
            return new EventResponse(id, regattaId, request.event_group_id(), request.category_id(), request.boat_type_id(), request.name(), request.display_order());
        } catch (Exception e) {
            throw mapMutationFailure("create event", e);
        }
    }

    public EventResponse updateEvent(UUID regattaId, UUID eventId, EventCreateRequest request) {
        Instant now = Instant.now();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE events
                 SET event_group_id = ?, category_id = ?, boat_type_id = ?, name = ?, display_order = ?, updated_at = ?
                 WHERE id = ? AND regatta_id = ?
                 """)) {
            statement.setObject(1, request.event_group_id());
            statement.setObject(2, request.category_id());
            statement.setObject(3, request.boat_type_id());
            statement.setString(4, request.name());
            statement.setInt(5, request.display_order());
            statement.setTimestamp(6, Timestamp.from(now));
            statement.setObject(7, eventId);
            statement.setObject(8, regattaId);
            if (statement.executeUpdate() == 0) {
                throw new SetupNotFoundException("Event not found");
            }
            return new EventResponse(eventId, regattaId, request.event_group_id(), request.category_id(), request.boat_type_id(), request.name(), request.display_order());
        } catch (SetupNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw mapMutationFailure("update event", e);
        }
    }

    public void deleteEvent(UUID regattaId, UUID eventId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 DELETE FROM events
                 WHERE id = ? AND regatta_id = ?
                 """)) {
            statement.setObject(1, eventId);
            statement.setObject(2, regattaId);
            if (statement.executeUpdate() == 0) {
                throw new SetupNotFoundException("Event not found");
            }
        } catch (SetupNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw mapMutationFailure("delete event", e);
        }
    }

    public ListResponse<EventResponse> listEvents(UUID regattaId, String search) {
        List<EventResponse> data = new ArrayList<>();
        String normalizedSearch = blankToNull(search == null ? null : search.trim().toLowerCase());

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT id, regatta_id, event_group_id, category_id, boat_type_id, name, display_order
                 FROM events
                 WHERE regatta_id = ?
                   AND (? IS NULL OR LOWER(name) LIKE ?)
                 ORDER BY display_order, name, id
                 """)) {
            statement.setObject(1, regattaId);
            statement.setString(2, normalizedSearch);
            statement.setString(3, normalizedSearch == null ? null : "%" + normalizedSearch + "%");

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    data.add(new EventResponse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("regatta_id", UUID.class),
                        rs.getObject("event_group_id", UUID.class),
                        rs.getObject("category_id", UUID.class),
                        rs.getObject("boat_type_id", UUID.class),
                        rs.getString("name"),
                        rs.getInt("display_order")
                    ));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list events", e);
        }

        return new ListResponse<>(data, new Pagination(false, null));
    }

    @Transactional
    public CrewResponse createCrew(UUID regattaId, CrewCreateRequest request) {
        UUID crewId = UUID.randomUUID();
        Instant now = Instant.now();
        List<CrewMemberResponse> members = new ArrayList<>();

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO crews (id, display_name, is_composite, club_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
                statement.setObject(1, crewId);
                statement.setString(2, request.display_name());
                statement.setBoolean(3, request.is_composite());
                statement.setObject(4, request.club_id());
                statement.setTimestamp(5, Timestamp.from(now));
                statement.setTimestamp(6, Timestamp.from(now));
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO regatta_crews (regatta_id, crew_id, created_at)
                VALUES (?, ?, ?)
                """)) {
                statement.setObject(1, regattaId);
                statement.setObject(2, crewId);
                statement.setTimestamp(3, Timestamp.from(now));
                statement.executeUpdate();
            }

            for (CrewMemberRequest member : request.members()) {
                UUID crewAthleteId = UUID.randomUUID();
                try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO crew_athletes (id, crew_id, athlete_id, seat_position)
                    VALUES (?, ?, ?, ?)
                    """)) {
                    statement.setObject(1, crewAthleteId);
                    statement.setObject(2, crewId);
                    statement.setObject(3, member.athlete_id());
                    statement.setInt(4, member.seat_position());
                    statement.executeUpdate();
                }
                members.add(new CrewMemberResponse(member.athlete_id(), member.seat_position()));
            }

            return new CrewResponse(crewId, request.display_name(), request.club_id(), request.is_composite(), members);
        } catch (Exception e) {
            throw mapMutationFailure("create crew", e);
        }
    }

    @Transactional
    public CrewResponse updateCrew(UUID regattaId, UUID crewId, CrewCreateRequest request) {
        Instant now = Instant.now();
        List<CrewMemberResponse> members = new ArrayList<>();

        try (Connection connection = dataSource.getConnection()) {
            if (!regattaCrewExists(connection, regattaId, crewId)) {
                throw new SetupNotFoundException("Crew not found");
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE crews
                SET display_name = ?, is_composite = ?, club_id = ?, updated_at = ?
                WHERE id = ?
                """)) {
                statement.setString(1, request.display_name());
                statement.setBoolean(2, request.is_composite());
                statement.setObject(3, request.club_id());
                statement.setTimestamp(4, Timestamp.from(now));
                statement.setObject(5, crewId);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM crew_athletes WHERE crew_id = ?")) {
                statement.setObject(1, crewId);
                statement.executeUpdate();
            }

            for (CrewMemberRequest member : request.members()) {
                UUID crewAthleteId = UUID.randomUUID();
                try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO crew_athletes (id, crew_id, athlete_id, seat_position)
                    VALUES (?, ?, ?, ?)
                    """)) {
                    statement.setObject(1, crewAthleteId);
                    statement.setObject(2, crewId);
                    statement.setObject(3, member.athlete_id());
                    statement.setInt(4, member.seat_position());
                    statement.executeUpdate();
                }
                members.add(new CrewMemberResponse(member.athlete_id(), member.seat_position()));
            }

            return new CrewResponse(crewId, request.display_name(), request.club_id(), request.is_composite(), members);
        } catch (SetupNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw mapMutationFailure("update crew", e);
        }
    }

    @Transactional
    public void deleteCrew(UUID regattaId, UUID crewId) {
        try (Connection connection = dataSource.getConnection()) {
            if (!regattaCrewExists(connection, regattaId, crewId)) {
                throw new SetupNotFoundException("Crew not found");
            }
            if (crewHasEntries(connection, regattaId, crewId)) {
                throw new ConflictException("Crew has entries and cannot be deleted", Map.of("crew_id", crewId.toString()));
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM regatta_crews
                WHERE regatta_id = ? AND crew_id = ?
                """)) {
                statement.setObject(1, regattaId);
                statement.setObject(2, crewId);
                statement.executeUpdate();
            }

            if (!hasAnyRegattaCrewAssociations(connection, crewId) && !crewHasEntries(connection, null, crewId)) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM crews WHERE id = ?")) {
                    statement.setObject(1, crewId);
                    statement.executeUpdate();
                }
            }
        } catch (SetupNotFoundException | ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw mapMutationFailure("delete crew", e);
        }
    }

    public ListResponse<CrewResponse> listCrews(UUID regattaId, String search) {
        List<CrewResponse> data = new ArrayList<>();
        String normalizedSearch = blankToNull(search == null ? null : search.trim().toLowerCase());

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT c.id, c.display_name, c.club_id, c.is_composite,
                        ca.athlete_id, ca.seat_position
                 FROM regatta_crews rc
                 JOIN crews c ON c.id = rc.crew_id
                 LEFT JOIN crew_athletes ca ON ca.crew_id = c.id
                 WHERE rc.regatta_id = ?
                   AND (? IS NULL OR LOWER(c.display_name) LIKE ?)
                 ORDER BY c.display_name, c.id, ca.seat_position
                 """)) {
            statement.setObject(1, regattaId);
            statement.setString(2, normalizedSearch);
            statement.setString(3, normalizedSearch == null ? null : "%" + normalizedSearch + "%");
            try (ResultSet rs = statement.executeQuery()) {
                UUID currentId = null;
                String currentDisplayName = null;
                UUID currentClubId = null;
                boolean currentComposite = false;
                List<CrewMemberResponse> currentMembers = new ArrayList<>();

                while (rs.next()) {
                    UUID crewId = rs.getObject("id", UUID.class);
                    if (!crewId.equals(currentId)) {
                        if (currentId != null) {
                            data.add(new CrewResponse(currentId, currentDisplayName, currentClubId, currentComposite, List.copyOf(currentMembers)));
                        }
                        currentId = crewId;
                        currentDisplayName = rs.getString("display_name");
                        currentClubId = rs.getObject("club_id", UUID.class);
                        currentComposite = rs.getBoolean("is_composite");
                        currentMembers = new ArrayList<>();
                    }

                    UUID athleteId = rs.getObject("athlete_id", UUID.class);
                    Integer seatPosition = (Integer) rs.getObject("seat_position");
                    if (athleteId != null && seatPosition != null) {
                        currentMembers.add(new CrewMemberResponse(athleteId, seatPosition));
                    }
                }

                if (currentId != null) {
                    data.add(new CrewResponse(currentId, currentDisplayName, currentClubId, currentComposite, List.copyOf(currentMembers)));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list crews", e);
        }

        return new ListResponse<>(data, new Pagination(false, null));
    }

    public EntryResponse createEntry(UUID regattaId, EntryCreateRequest request) {
        EntryDto entry = entryService.createEntry(
            regattaId,
            request.event_id(),
            request.block_id(),
            request.crew_id(),
            request.billing_club_id()
        );
        return EntryResponse.from(entry);
    }

    public EntryResponse updateEntry(UUID regattaId, UUID entryId, EntryCreateRequest request) {
        Instant now = Instant.now();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE entries
                 SET event_id = ?, block_id = ?, crew_id = ?, billing_club_id = ?, updated_at = ?
                 WHERE id = ? AND regatta_id = ?
                 """)) {
            statement.setObject(1, request.event_id());
            statement.setObject(2, request.block_id());
            statement.setObject(3, request.crew_id());
            statement.setObject(4, request.billing_club_id());
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setObject(6, entryId);
            statement.setObject(7, regattaId);
            if (statement.executeUpdate() == 0) {
                throw new SetupNotFoundException("Entry not found");
            }
            return loadEntryResponse(connection, regattaId, entryId);
        } catch (SetupNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw mapMutationFailure("update entry", e);
        }
    }

    public void deleteEntry(UUID regattaId, UUID entryId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 DELETE FROM entries
                 WHERE id = ? AND regatta_id = ?
                 """)) {
            statement.setObject(1, entryId);
            statement.setObject(2, regattaId);
            if (statement.executeUpdate() == 0) {
                throw new SetupNotFoundException("Entry not found");
            }
        } catch (SetupNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw mapMutationFailure("delete entry", e);
        }
    }

    public ListResponse<EntryResponse> listEntries(UUID regattaId, String status, String search) {
        List<EntryResponse> data = new ArrayList<>();
        String normalizedSearch = blankToNull(search == null ? null : search.trim().toLowerCase());

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT e.id, e.regatta_id, e.event_id, e.block_id, e.crew_id, e.billing_club_id, e.status, e.payment_status, e.paid_at, e.paid_by,
                        e.payment_reference, e.marker_start_time_ms, e.marker_finish_time_ms, e.completion_status
                 FROM entries e
                 LEFT JOIN crews c ON c.id = e.crew_id
                 LEFT JOIN events ev ON ev.id = e.event_id
                 WHERE e.regatta_id = ?
                   AND (? IS NULL OR e.status = ?)
                   AND (
                        ? IS NULL
                        OR LOWER(COALESCE(c.display_name, '')) LIKE ?
                        OR LOWER(COALESCE(ev.name, '')) LIKE ?
                        OR CAST(e.id AS VARCHAR(36)) LIKE ?
                   )
                 ORDER BY e.created_at, e.id
                 """)) {
            statement.setObject(1, regattaId);
            statement.setString(2, blankToNull(status));
            statement.setString(3, blankToNull(status));
            statement.setString(4, normalizedSearch);
            statement.setString(5, normalizedSearch == null ? null : "%" + normalizedSearch + "%");
            statement.setString(6, normalizedSearch == null ? null : "%" + normalizedSearch + "%");
            statement.setString(7, normalizedSearch == null ? null : "%" + normalizedSearch + "%");

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    data.add(new EntryResponse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("regatta_id", UUID.class),
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("block_id", UUID.class),
                        rs.getObject("crew_id", UUID.class),
                        rs.getObject("billing_club_id", UUID.class),
                        rs.getString("status"),
                        rs.getString("payment_status"),
                        rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toInstant(),
                        rs.getString("paid_by"),
                        rs.getString("payment_reference"),
                        (Long) rs.getObject("marker_start_time_ms"),
                        (Long) rs.getObject("marker_finish_time_ms"),
                        rs.getString("completion_status")
                    ));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list entries", e);
        }

        return new ListResponse<>(data, new Pagination(false, null));
    }

    @Transactional
    public WithdrawResponse withdrawEntry(UUID regattaId, UUID entryId, WithdrawEntryRequest request, String actor) {
        validateWithdrawStatus(request.status());

        Instant now = Instant.now();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE entries
                 SET status = ?, updated_at = ?
                 WHERE id = ? AND regatta_id = ? AND status = ?
                 """)) {
            statement.setString(1, request.status());
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setObject(3, entryId);
            statement.setObject(4, regattaId);
            statement.setString(5, request.expected_status());
            if (statement.executeUpdate() == 0) {
                throwConflictOrNotFound(regattaId, entryId, "Entry status changed before withdraw could be applied");
            }
            return new WithdrawResponse(entryId, request.status(), new AuditResponse(actor, now, request.reason()));
        } catch (ConflictException | EntryNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to withdraw entry", e);
        }
    }

    @Transactional
    public WithdrawResponse reinstateEntry(UUID regattaId, UUID entryId, ReinstateEntryRequest request, String actor) {
        Instant now = Instant.now();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE entries
                 SET status = ?, updated_at = ?
                 WHERE id = ? AND regatta_id = ? AND status = ?
                 """)) {
            statement.setString(1, "entered");
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setObject(3, entryId);
            statement.setObject(4, regattaId);
            statement.setString(5, request.expected_status());
            if (statement.executeUpdate() == 0) {
                throwConflictOrNotFound(regattaId, entryId, "Entry status changed before reinstate could be applied");
            }
            return new WithdrawResponse(entryId, "entered", new AuditResponse(actor, now, null));
        } catch (ConflictException | EntryNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to reinstate entry", e);
        }
    }

    private void validateWithdrawStatus(String status) {
        if (!WITHDRAW_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unsupported withdraw status: " + status);
        }
    }

    private void throwConflictOrNotFound(UUID regattaId, UUID entryId, String message) {
        EntryState state = requireEntry(regattaId, entryId);
        throw new ConflictException(message, Map.of("current_status", state.status()));
    }

    private EntryResponse loadEntryResponse(Connection connection, UUID regattaId, UUID entryId) {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT id, regatta_id, event_id, block_id, crew_id, billing_club_id, status, payment_status, paid_at, paid_by,
                   payment_reference, marker_start_time_ms, marker_finish_time_ms, completion_status
            FROM entries
            WHERE id = ? AND regatta_id = ?
            """)) {
            statement.setObject(1, entryId);
            statement.setObject(2, regattaId);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new EntryResponse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("regatta_id", UUID.class),
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("block_id", UUID.class),
                        rs.getObject("crew_id", UUID.class),
                        rs.getObject("billing_club_id", UUID.class),
                        rs.getString("status"),
                        rs.getString("payment_status"),
                        rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toInstant(),
                        rs.getString("paid_by"),
                        rs.getString("payment_reference"),
                        (Long) rs.getObject("marker_start_time_ms"),
                        (Long) rs.getObject("marker_finish_time_ms"),
                        rs.getString("completion_status")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load entry", e);
        }
        throw new SetupNotFoundException("Entry not found");
    }

    private EntryState requireEntry(UUID regattaId, UUID entryId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT id, status
                 FROM entries
                 WHERE id = ? AND regatta_id = ?
                 """)) {
            statement.setObject(1, entryId);
            statement.setObject(2, regattaId);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new EntryState(rs.getObject("id", UUID.class), rs.getString("status"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load entry", e);
        }
        throw new EntryNotFoundException(entryId);
    }

    private boolean regattaCrewExists(Connection connection, UUID regattaId, UUID crewId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT 1
            FROM regatta_crews
            WHERE regatta_id = ? AND crew_id = ?
            """)) {
            statement.setObject(1, regattaId);
            statement.setObject(2, crewId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasAnyRegattaCrewAssociations(Connection connection, UUID crewId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT 1
            FROM regatta_crews
            WHERE crew_id = ?
            """)) {
            statement.setObject(1, crewId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean crewHasEntries(Connection connection, UUID regattaId, UUID crewId) throws SQLException {
        String sql = regattaId == null
            ? "SELECT 1 FROM entries WHERE crew_id = ?"
            : "SELECT 1 FROM entries WHERE regatta_id = ? AND crew_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = 1;
            if (regattaId != null) {
                statement.setObject(parameterIndex++, regattaId);
            }
            statement.setObject(parameterIndex, crewId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private RuntimeException mapMutationFailure(String operation, Exception error) {
        SQLException sqlException = findSqlException(error);
        if (sqlException != null && sqlException.getSQLState() != null && sqlException.getSQLState().startsWith("23")) {
            return new SetupBadRequestException("Request references invalid or conflicting setup data");
        }
        return new RuntimeException("Failed to " + operation, error);
    }

    private SQLException findSqlException(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }

    public static final class ConflictException extends RuntimeException {
        private final Map<String, Object> details;

        public ConflictException(String message, Map<String, Object> details) {
            super(message);
            this.details = details;
        }

        public Map<String, Object> details() {
            return details;
        }
    }

    public static final class SetupNotFoundException extends RuntimeException {
        public SetupNotFoundException(String message) {
            super(message);
        }
    }

    public static final class SetupBadRequestException extends RuntimeException {
        public SetupBadRequestException(String message) {
            super(message);
        }
    }

    private record EntryState(UUID id, String status) {
    }
}
