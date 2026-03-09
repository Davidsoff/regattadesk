package com.regattadesk.export;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for fetching regatta metadata required for PDF generation.
 */
@ApplicationScoped
public class ExportRegattaRepository {

    @Inject
    DataSource dataSource;

    /**
     * Fetches the metadata needed to generate a printable PDF for a regatta.
     *
     * @param regattaId regatta UUID
     * @return optional metadata, empty when regatta not found
     * @throws SQLException on query failure
     */
    public Optional<RegattaMetadata> findMetadata(UUID regattaId) throws SQLException {
        String sql = """
                SELECT name, time_zone, draw_revision, results_revision
                FROM regattas
                WHERE id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regattaId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new RegattaMetadata(
                            rs.getString("name"),
                            rs.getString("time_zone"),
                            rs.getInt("draw_revision"),
                            rs.getInt("results_revision")
                    ));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Metadata required for PDF header generation.
     *
     * @param name             regatta name
     * @param timeZone         IANA time-zone identifier for the regatta
     * @param drawRevision     current draw revision
     * @param resultsRevision  current results revision
     */
    public record RegattaMetadata(
            String name,
            String timeZone,
            int drawRevision,
            int resultsRevision
    ) {}
}
