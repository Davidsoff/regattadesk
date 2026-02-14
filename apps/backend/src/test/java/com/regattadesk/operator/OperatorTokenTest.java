package com.regattadesk.operator;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OperatorTokenTest {
    
    @Test
    void testCreateToken_ValidParameters() {
        UUID id = UUID.randomUUID();
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        String station = "start-line";
        String token = "test-token-123";
        String pin = "123456";
        Instant validFrom = Instant.now();
        Instant validUntil = validFrom.plus(1, ChronoUnit.HOURS);
        Instant now = Instant.now();
        
        OperatorToken operatorToken = new OperatorToken(
            id, regattaId, blockId, station, token, pin,
            validFrom, validUntil, true, now, now
        );
        
        assertNotNull(operatorToken);
        assertEquals(id, operatorToken.getId());
        assertEquals(regattaId, operatorToken.getRegattaId());
        assertEquals(blockId, operatorToken.getBlockId());
        assertEquals(station, operatorToken.getStation());
        assertEquals(token, operatorToken.getToken());
        assertEquals(pin, operatorToken.getPin());
        assertEquals(validFrom, operatorToken.getValidFrom());
        assertEquals(validUntil, operatorToken.getValidUntil());
        assertTrue(operatorToken.isActive());
    }
    
    @Test
    void testCreateToken_NullId_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OperatorToken(
                null, UUID.randomUUID(), null, "station", "token", null,
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                true, Instant.now(), Instant.now()
            );
        });
    }
    
    @Test
    void testCreateToken_NullRegattaId_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OperatorToken(
                UUID.randomUUID(), null, null, "station", "token", null,
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                true, Instant.now(), Instant.now()
            );
        });
    }
    
    @Test
    void testCreateToken_BlankStation_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OperatorToken(
                UUID.randomUUID(), UUID.randomUUID(), null, "", "token", null,
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                true, Instant.now(), Instant.now()
            );
        });
    }

    @Test
    void testCreateToken_NullStation_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OperatorToken(
                UUID.randomUUID(), UUID.randomUUID(), null, null, "token", null,
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                true, Instant.now(), Instant.now()
            );
        });
    }
    
    @Test
    void testCreateToken_BlankToken_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OperatorToken(
                UUID.randomUUID(), UUID.randomUUID(), null, "station", "  ", null,
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                true, Instant.now(), Instant.now()
            );
        });
    }
    
    @Test
    void testCreateToken_ValidUntilBeforeValidFrom_ThrowsException() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> {
            new OperatorToken(
                UUID.randomUUID(), UUID.randomUUID(), null, "station", "token", null,
                now, now.minus(1, ChronoUnit.HOURS),
                true, now, now
            );
        });
    }
    
    @Test
    void testIsValidAt_ValidToken() {
        Instant now = Instant.now();
        Instant validFrom = now.minus(1, ChronoUnit.HOURS);
        Instant validUntil = now.plus(1, ChronoUnit.HOURS);
        
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(), UUID.randomUUID(), null, "station", "token", null,
            validFrom, validUntil, true, now, now
        );
        
        assertTrue(token.isValidAt(now));
        assertTrue(token.isValidAt(validFrom));
        assertFalse(token.isValidAt(validUntil));
        assertFalse(token.isValidAt(validUntil.plus(1, ChronoUnit.SECONDS)));
    }
    
    @Test
    void testIsValidAt_RevokedToken() {
        Instant now = Instant.now();
        Instant validFrom = now.minus(1, ChronoUnit.HOURS);
        Instant validUntil = now.plus(1, ChronoUnit.HOURS);
        
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(), UUID.randomUUID(), null, "station", "token", null,
            validFrom, validUntil, false, now, now
        );
        
        assertFalse(token.isValidAt(now));
    }
    
    @Test
    void testIsValidAt_BeforeValidFrom() {
        Instant now = Instant.now();
        Instant validFrom = now.plus(1, ChronoUnit.HOURS);
        Instant validUntil = now.plus(2, ChronoUnit.HOURS);
        
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(), UUID.randomUUID(), null, "station", "token", null,
            validFrom, validUntil, true, now, now
        );
        
        assertFalse(token.isValidAt(now));
    }
    
    @Test
    void testIsValidAt_AfterValidUntil() {
        Instant now = Instant.now();
        Instant validFrom = now.minus(2, ChronoUnit.HOURS);
        Instant validUntil = now.minus(1, ChronoUnit.HOURS);
        
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(), UUID.randomUUID(), null, "station", "token", null,
            validFrom, validUntil, true, now, now
        );
        
        assertFalse(token.isValidAt(now));
    }
    
    @Test
    void testIsExpired_ExpiredToken() {
        Instant now = Instant.now();
        Instant validFrom = now.minus(2, ChronoUnit.HOURS);
        Instant validUntil = now.minus(1, ChronoUnit.HOURS);
        
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(), UUID.randomUUID(), null, "station", "token", null,
            validFrom, validUntil, true, now, now
        );
        
        assertTrue(token.isExpired());
    }
    
    @Test
    void testIsExpired_NotExpiredToken() {
        Instant now = Instant.now();
        Instant validFrom = now.minus(1, ChronoUnit.HOURS);
        Instant validUntil = now.plus(1, ChronoUnit.HOURS);
        
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(), UUID.randomUUID(), null, "station", "token", null,
            validFrom, validUntil, true, now, now
        );
        
        assertFalse(token.isExpired());
    }
    
    @Test
    void testHasBlockScope_WithBlock() {
        Instant now = Instant.now();
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "station", "token", null,
            now, now.plus(1, ChronoUnit.HOURS), true, now, now
        );
        
        assertTrue(token.hasBlockScope());
    }
    
    @Test
    void testHasBlockScope_WithoutBlock() {
        Instant now = Instant.now();
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(), UUID.randomUUID(), null,
            "station", "token", null,
            now, now.plus(1, ChronoUnit.HOURS), true, now, now
        );
        
        assertFalse(token.hasBlockScope());
    }
    
    @Test
    void testEquals_SameId() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        
        OperatorToken token1 = new OperatorToken(
            id, UUID.randomUUID(), null, "station1", "token1", null,
            now, now.plus(1, ChronoUnit.HOURS), true, now, now
        );
        
        OperatorToken token2 = new OperatorToken(
            id, UUID.randomUUID(), null, "station2", "token2", null,
            now, now.plus(1, ChronoUnit.HOURS), true, now, now
        );
        
        assertEquals(token1, token2);
        assertEquals(token1.hashCode(), token2.hashCode());
    }
    
    @Test
    void testEquals_DifferentId() {
        Instant now = Instant.now();
        
        OperatorToken token1 = new OperatorToken(
            UUID.randomUUID(), UUID.randomUUID(), null, "station", "token", null,
            now, now.plus(1, ChronoUnit.HOURS), true, now, now
        );
        
        OperatorToken token2 = new OperatorToken(
            UUID.randomUUID(), UUID.randomUUID(), null, "station", "token", null,
            now, now.plus(1, ChronoUnit.HOURS), true, now, now
        );
        
        assertNotEquals(token1, token2);
    }
}
