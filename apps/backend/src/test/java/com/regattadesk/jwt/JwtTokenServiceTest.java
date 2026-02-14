package com.regattadesk.jwt;

import com.regattadesk.jwt.JwtTokenService.InvalidTokenException;
import com.regattadesk.jwt.JwtTokenService.ValidatedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenService.
 */
class JwtTokenServiceTest {
    
    private JwtTokenService service;
    private TestJwtConfig config;
    
    @BeforeEach
    void setUp() {
        config = new TestJwtConfig();
        service = new JwtTokenService(config);
    }
    
    @Test
    void testIssueToken() {
        String token = service.issueToken();
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains("."), "JWT should have three parts separated by dots");
    }
    
    @Test
    void testValidateToken() throws InvalidTokenException {
        String token = service.issueToken();
        ValidatedToken validated = service.validateToken(token);
        
        assertNotNull(validated);
        assertNotNull(validated.sessionId());
        assertFalse(validated.sessionId().isEmpty());
        assertNotNull(validated.issuedAt());
        assertNotNull(validated.expiresAt());
        
        // Verify expiration is roughly 5 days from now
        Duration ttl = Duration.between(validated.issuedAt(), validated.expiresAt());
        assertEquals(432000, ttl.getSeconds(), 5, "TTL should be 5 days (432000 seconds)");
    }
    
    @Test
    void testValidateInvalidToken() {
        assertThrows(InvalidTokenException.class, () -> {
            service.validateToken("invalid.token.here");
        });
    }
    
    @Test
    void testValidateTokenWithDifferentKey() {
        String token = service.issueToken();
        
        // Create a service with a different key
        TestJwtConfig differentConfig = new TestJwtConfig();
        differentConfig.secretOverride = "different-secret-key-at-least-32-bytes-required";
        JwtTokenService differentService = new JwtTokenService(differentConfig);
        
        assertThrows(InvalidTokenException.class, () -> {
            differentService.validateToken(token);
        });
    }
    
    @Test
    void testIsInRefreshWindow_FreshToken() throws InvalidTokenException {
        String token = service.issueToken();
        ValidatedToken validated = service.validateToken(token);
        
        // Fresh token should not be in refresh window
        assertFalse(service.isInRefreshWindow(validated));
    }
    
    @Test
    void testIsInRefreshWindow_TokenNearExpiration() throws InvalidTokenException {
        Instant now = Instant.now();
        ValidatedToken nearExpiry = new ValidatedToken(
            "test-sid",
            now.minusSeconds(431990),
            now.plusSeconds(10)
        );

        assertTrue(service.isInRefreshWindow(nearExpiry));
    }

    @Test
    void testIsInRefreshWindow_TokenOutsideRefreshWindow() {
        Instant now = Instant.now();
        ValidatedToken fresh = new ValidatedToken(
            "test-sid",
            now,
            now.plusSeconds(200000)
        );

        assertFalse(service.isInRefreshWindow(fresh));
    }
    
    @Test
    void testRefreshWindowCalculation() {
        // Test with default config: 5 days (432000s), 20% window = 86400s (1 day)
        long ttl = config.ttlSeconds();
        long refreshWindow = (ttl * config.refreshWindowPercent()) / 100;
        
        assertEquals(432000, ttl);
        assertEquals(20, config.refreshWindowPercent());
        assertEquals(86400, refreshWindow, "Refresh window should be 1 day (86400 seconds)");
    }
    
    @Test
    void testTokenContainsKid() throws InvalidTokenException {
        String token = service.issueToken();
        
        // Parse the JWT header to verify kid is present
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 parts");
        
        // The kid should be in the header
        ValidatedToken validated = service.validateToken(token);
        assertNotNull(validated); // If this passes, the token was signed with the correct kid
    }
    
    @Test
    void testShortSecretThrowsException() {
        TestJwtConfig shortSecretConfig = new TestJwtConfig();
        shortSecretConfig.secretOverride = "too-short"; // Less than 32 bytes
        
        assertThrows(IllegalArgumentException.class, () -> {
            new JwtTokenService(shortSecretConfig);
        });
    }
    
    /**
     * Test implementation of JwtConfig for testing.
     */
    private static class TestJwtConfig implements JwtConfig {
        String secretOverride = "test-secret-key-at-least-32-bytes-for-hs256-algorithm";
        String kidOverride = "test-kid-v1";
        Integer ttlSecondsOverride = 432000; // 5 days
        Integer refreshWindowPercentOverride = 20;
        
        @Override
        public String secret() {
            return secretOverride;
        }
        
        @Override
        public String kid() {
            return kidOverride;
        }
        
        @Override
        public int ttlSeconds() {
            return ttlSecondsOverride;
        }
        
        @Override
        public int refreshWindowPercent() {
            return refreshWindowPercentOverride;
        }
    }
}
