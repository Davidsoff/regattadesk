package com.regattadesk.operator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OperatorTokenValidatorTest {
    
    private OperatorTokenValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new OperatorTokenValidator();
    }
    
    @Test
    void testValidateToken_ValidToken() {
        Instant now = Instant.now();
        OperatorToken token = createToken(
            now.minus(1, ChronoUnit.HOURS),
            now.plus(1, ChronoUnit.HOURS),
            true
        );
        
        OperatorTokenValidator.ValidationResult result = validator.validateToken(token, now);
        
        assertEquals(OperatorTokenValidator.ValidationResult.VALID, result);
        assertTrue(result.isValid());
    }
    
    @Test
    void testValidateToken_RevokedToken() {
        Instant now = Instant.now();
        OperatorToken token = createToken(
            now.minus(1, ChronoUnit.HOURS),
            now.plus(1, ChronoUnit.HOURS),
            false
        );
        
        OperatorTokenValidator.ValidationResult result = validator.validateToken(token, now);
        
        assertEquals(OperatorTokenValidator.ValidationResult.REVOKED, result);
        assertFalse(result.isValid());
    }
    
    @Test
    void testValidateToken_ExpiredToken() {
        Instant now = Instant.now();
        OperatorToken token = createToken(
            now.minus(2, ChronoUnit.HOURS),
            now.minus(1, ChronoUnit.HOURS),
            true
        );
        
        OperatorTokenValidator.ValidationResult result = validator.validateToken(token, now);
        
        assertEquals(OperatorTokenValidator.ValidationResult.EXPIRED, result);
        assertFalse(result.isValid());
    }
    
    @Test
    void testValidateToken_NotYetValid() {
        Instant now = Instant.now();
        OperatorToken token = createToken(
            now.plus(1, ChronoUnit.HOURS),
            now.plus(2, ChronoUnit.HOURS),
            true
        );
        
        OperatorTokenValidator.ValidationResult result = validator.validateToken(token, now);
        
        assertEquals(OperatorTokenValidator.ValidationResult.NOT_YET_VALID, result);
        assertFalse(result.isValid());
    }

    @Test
    void testValidateToken_AtExactValidFrom() {
        Instant validFrom = Instant.now();
        OperatorToken token = createToken(
            validFrom,
            validFrom.plus(1, ChronoUnit.HOURS),
            true
        );

        OperatorTokenValidator.ValidationResult result = validator.validateToken(token, validFrom);

        assertEquals(OperatorTokenValidator.ValidationResult.VALID, result);
        assertTrue(result.isValid());
    }

    @Test
    void testValidateToken_AtExactValidUntil() {
        Instant now = Instant.now();
        Instant validUntil = now.plus(1, ChronoUnit.HOURS);
        OperatorToken token = createToken(now, validUntil, true);

        OperatorTokenValidator.ValidationResult result = validator.validateToken(token, validUntil);

        assertEquals(OperatorTokenValidator.ValidationResult.EXPIRED, result);
        assertFalse(result.isValid());
    }
    
    @Test
    void testValidateRegattaScope_ValidScope() {
        UUID regattaId = UUID.randomUUID();
        OperatorToken token = createToken(regattaId, null, "station");
        
        OperatorTokenValidator.ValidationResult result = validator.validateRegattaScope(token, regattaId);
        
        assertEquals(OperatorTokenValidator.ValidationResult.VALID, result);
    }
    
    @Test
    void testValidateRegattaScope_InvalidScope() {
        OperatorToken token = createToken(UUID.randomUUID(), null, "station");
        
        OperatorTokenValidator.ValidationResult result = validator.validateRegattaScope(token, UUID.randomUUID());
        
        assertEquals(OperatorTokenValidator.ValidationResult.INVALID_REGATTA_SCOPE, result);
    }
    
    @Test
    void testValidateStationScope_ValidScope() {
        String station = "start-line";
        OperatorToken token = createToken(UUID.randomUUID(), null, station);
        
        OperatorTokenValidator.ValidationResult result = validator.validateStationScope(token, station);
        
        assertEquals(OperatorTokenValidator.ValidationResult.VALID, result);
    }
    
    @Test
    void testValidateStationScope_InvalidScope() {
        OperatorToken token = createToken(UUID.randomUUID(), null, "start-line");
        
        OperatorTokenValidator.ValidationResult result = validator.validateStationScope(token, "finish-line");
        
        assertEquals(OperatorTokenValidator.ValidationResult.INVALID_STATION_SCOPE, result);
    }
    
    @Test
    void testValidateBlockScope_NoBlockScope() {
        OperatorToken token = createToken(UUID.randomUUID(), null, "station");
        
        OperatorTokenValidator.ValidationResult result = validator.validateBlockScope(token, UUID.randomUUID());
        
        assertEquals(OperatorTokenValidator.ValidationResult.VALID, result);
    }
    
    @Test
    void testValidateBlockScope_ValidBlockScope() {
        UUID blockId = UUID.randomUUID();
        OperatorToken token = createToken(UUID.randomUUID(), blockId, "station");
        
        OperatorTokenValidator.ValidationResult result = validator.validateBlockScope(token, blockId);
        
        assertEquals(OperatorTokenValidator.ValidationResult.VALID, result);
    }
    
    @Test
    void testValidateBlockScope_InvalidBlockScope() {
        OperatorToken token = createToken(UUID.randomUUID(), UUID.randomUUID(), "station");
        
        OperatorTokenValidator.ValidationResult result = validator.validateBlockScope(token, UUID.randomUUID());
        
        assertEquals(OperatorTokenValidator.ValidationResult.INVALID_BLOCK_SCOPE, result);
    }
    
    @Test
    void testValidateFull_AllValid() {
        Instant now = Instant.now();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        String station = "start-line";
        
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(),
            regattaId,
            blockId,
            station,
            "token",
            null,
            now.minus(1, ChronoUnit.HOURS),
            now.plus(1, ChronoUnit.HOURS),
            true,
            now,
            now
        );
        
        OperatorTokenValidator.ValidationResult result = validator.validateFull(
            token, now, regattaId, station, blockId
        );
        
        assertEquals(OperatorTokenValidator.ValidationResult.VALID, result);
    }
    
    @Test
    void testValidateFull_FailsOnExpired() {
        Instant now = Instant.now();
        UUID regattaId = UUID.randomUUID();
        String station = "start-line";
        
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(),
            regattaId,
            null,
            station,
            "token",
            null,
            now.minus(2, ChronoUnit.HOURS),
            now.minus(1, ChronoUnit.HOURS),
            true,
            now,
            now
        );
        
        OperatorTokenValidator.ValidationResult result = validator.validateFull(
            token, now, regattaId, station, null
        );
        
        assertEquals(OperatorTokenValidator.ValidationResult.EXPIRED, result);
    }
    
    @Test
    void testValidateFull_FailsOnInvalidRegatta() {
        Instant now = Instant.now();
        String station = "start-line";
        
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            station,
            "token",
            null,
            now.minus(1, ChronoUnit.HOURS),
            now.plus(1, ChronoUnit.HOURS),
            true,
            now,
            now
        );
        
        OperatorTokenValidator.ValidationResult result = validator.validateFull(
            token, now, UUID.randomUUID(), station, null
        );
        
        assertEquals(OperatorTokenValidator.ValidationResult.INVALID_REGATTA_SCOPE, result);
    }
    
    @Test
    void testValidateFull_FailsOnInvalidStation() {
        Instant now = Instant.now();
        UUID regattaId = UUID.randomUUID();
        
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(),
            regattaId,
            null,
            "start-line",
            "token",
            null,
            now.minus(1, ChronoUnit.HOURS),
            now.plus(1, ChronoUnit.HOURS),
            true,
            now,
            now
        );
        
        OperatorTokenValidator.ValidationResult result = validator.validateFull(
            token, now, regattaId, "finish-line", null
        );
        
        assertEquals(OperatorTokenValidator.ValidationResult.INVALID_STATION_SCOPE, result);
    }
    
    @Test
    void testValidateFull_FailsOnInvalidBlock() {
        Instant now = Instant.now();
        UUID regattaId = UUID.randomUUID();
        String station = "start-line";
        
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(),
            regattaId,
            UUID.randomUUID(),
            station,
            "token",
            null,
            now.minus(1, ChronoUnit.HOURS),
            now.plus(1, ChronoUnit.HOURS),
            true,
            now,
            now
        );
        
        OperatorTokenValidator.ValidationResult result = validator.validateFull(
            token, now, regattaId, station, UUID.randomUUID()
        );
        
        assertEquals(OperatorTokenValidator.ValidationResult.INVALID_BLOCK_SCOPE, result);
    }
    
    private OperatorToken createToken(Instant validFrom, Instant validUntil, boolean active) {
        Instant now = Instant.now();
        return new OperatorToken(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "station",
            "token",
            null,
            validFrom,
            validUntil,
            active,
            now,
            now
        );
    }
    
    private OperatorToken createToken(UUID regattaId, UUID blockId, String station) {
        Instant now = Instant.now();
        return new OperatorToken(
            UUID.randomUUID(),
            regattaId,
            blockId,
            station,
            "token",
            null,
            now.minus(1, ChronoUnit.HOURS),
            now.plus(1, ChronoUnit.HOURS),
            true,
            now,
            now
        );
    }
}
