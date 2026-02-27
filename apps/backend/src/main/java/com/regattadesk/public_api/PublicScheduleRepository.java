package com.regattadesk.public_api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository for public schedule rows keyed by regatta + draw revision.
 */
@ApplicationScoped
public class PublicScheduleRepository {

    @Inject
    DataSource dataSource;

    public List<ScheduleRow> fetchSchedule(UUID regattaId, int drawRevision) throws SQLException {
        String sql = """
            SELECT entry_id, event_id, bib, lane, scheduled_start_time, crew_name, club_name, status
            FROM public_regatta_draw
            WHERE regatta_id = ? AND draw_revision = ?
            ORDER BY scheduled_start_time NULLS LAST, event_id, lane NULLS LAST, bib NULLS LAST, entry_id
            """;

        List<ScheduleRow> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            stmt.setInt(2, drawRevision);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ScheduleRow(
                        rs.getObject("entry_id", UUID.class),
                        rs.getObject("event_id", UUID.class),
                        (Integer) rs.getObject("bib"),
                        (Integer) rs.getObject("lane"),
                        rs.getObject("scheduled_start_time", OffsetDateTime.class),
                        rs.getString("crew_name"),
                        rs.getString("club_name"),
                        rs.getString("status")
                    ));
                }
            }
        }

        return rows;
    }

    public record ScheduleRow(
        UUID entryId,
        UUID eventId,
        Integer bib,
        Integer lane,
        OffsetDateTime scheduledStartTime,
        String crewName,
        String clubName,
        String status
    ) {}
}
