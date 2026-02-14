package com.regattadesk.operator;

import com.regattadesk.operator.events.OperatorTokenIssuedEvent;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcOperatorTokenRepositoryTest {

    private JdbcOperatorTokenRepository repository;
    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:operator_repo_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        repository = new JdbcOperatorTokenRepository(dataSource);

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS event_store");
            stmt.execute("DROP TABLE IF EXISTS aggregates");
            stmt.execute("DROP TABLE IF EXISTS operator_tokens");
            stmt.execute("""
                CREATE TABLE operator_tokens (
                    id UUID PRIMARY KEY,
                    regatta_id UUID NOT NULL,
                    block_id UUID,
                    station VARCHAR(100) NOT NULL,
                    token VARCHAR(64) NOT NULL UNIQUE,
                    pin CHAR(6),
                    valid_from TIMESTAMP NOT NULL,
                    valid_until TIMESTAMP NOT NULL,
                    is_active BOOLEAN NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE aggregates (
                    id UUID PRIMARY KEY,
                    aggregate_type VARCHAR(100) NOT NULL,
                    version BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE event_store (
                    id UUID PRIMARY KEY,
                    aggregate_id UUID NOT NULL,
                    event_type VARCHAR(100) NOT NULL,
                    sequence_number BIGINT NOT NULL,
                    payload VARCHAR(10000) NOT NULL,
                    metadata VARCHAR(10000) NOT NULL,
                    correlation_id UUID,
                    causation_id UUID,
                    created_at TIMESTAMP NOT NULL,
                    UNIQUE(aggregate_id, sequence_number)
                )
                """);
        }
    }

    @Test
    void saveAndFindByToken_shouldRoundTrip() {
        UUID regattaId = UUID.randomUUID();
        Instant now = Instant.now();
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(),
            regattaId,
            null,
            "start-line",
            "token-123",
            "123456",
            now.minus(1, ChronoUnit.HOURS),
            now.plus(1, ChronoUnit.HOURS),
            true,
            now,
            now
        );

        repository.save(token);
        OperatorToken loaded = repository.findByToken("token-123").orElseThrow();

        assertEquals(token.getId(), loaded.getId());
        assertEquals(regattaId, loaded.getRegattaId());
    }

    @Test
    void revokeAndFindValidTokens_shouldReflectActiveState() {
        UUID regattaId = UUID.randomUUID();
        Instant now = Instant.now();
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(),
            regattaId,
            null,
            "results",
            "token-456",
            "654321",
            now.minus(1, ChronoUnit.HOURS),
            now.plus(1, ChronoUnit.HOURS),
            true,
            now,
            now
        );
        repository.save(token);

        List<OperatorToken> validBefore = repository.findValidTokens(regattaId, now);
        assertEquals(1, validBefore.size());

        repository.revoke(token.getId());
        List<OperatorToken> validAfter = repository.findValidTokens(regattaId, now);
        assertTrue(validAfter.isEmpty());
    }

    @Test
    void appendEvent_shouldPersistAuditEvent() throws Exception {
        UUID tokenId = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        OperatorTokenIssuedEvent event = new OperatorTokenIssuedEvent(
            tokenId,
            regattaId,
            null,
            "start",
            Instant.now(),
            Instant.now().plus(1, ChronoUnit.HOURS),
            Instant.now(),
            "tester"
        );

        repository.appendEvent(event);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM event_store WHERE aggregate_id = '" + tokenId + "'")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }
}
