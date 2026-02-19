package com.regattadesk.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.regattadesk.jwt.JwtTokenService.InvalidTokenException;
import com.regattadesk.jwt.JwtTokenService.ValidatedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

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
        assertEquals(3, token.split("\\.").length, "JWT should have exactly 3 segments");
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
    void testTokenContainsKid() throws ParseException {
        String token = service.issueToken();

        SignedJWT signedJWT = SignedJWT.parse(token);
        assertEquals(config.kid(), signedJWT.getHeader().getKeyID(), "JWT kid header mismatch");
        assertEquals(JWSAlgorithm.HS256, signedJWT.getHeader().getAlgorithm(), "JWT algorithm mismatch");
    }
    
    @Test
    void testShortSecretThrowsException() {
        TestJwtConfig shortSecretConfig = new TestJwtConfig();
        shortSecretConfig.secretOverride = "too-short"; // Less than 32 bytes
        
        assertThrows(IllegalArgumentException.class, () -> {
            new JwtTokenService(shortSecretConfig);
        });
    }

    @Test
    void testValidateTokenMissingSid() throws Exception {
        String token = signToken(
            new JWTClaimsSet.Builder()
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .build()
        );

        assertThrows(InvalidTokenException.class, () -> service.validateToken(token));
    }

    @Test
    void testValidateTokenMissingIat() throws Exception {
        String token = signToken(
            new JWTClaimsSet.Builder()
                .claim("sid", UUID.randomUUID().toString())
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .build()
        );

        assertThrows(InvalidTokenException.class, () -> service.validateToken(token));
    }

    @Test
    void testValidateTokenMissingExp() throws Exception {
        String token = signToken(
            new JWTClaimsSet.Builder()
                .claim("sid", UUID.randomUUID().toString())
                .issueTime(Date.from(Instant.now()))
                .build()
        );

        assertThrows(InvalidTokenException.class, () -> service.validateToken(token));
    }

    private String signToken(JWTClaimsSet claims) throws Exception {
        SignedJWT token = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(config.kid()).build(),
            claims
        );
        token.sign(new MACSigner(config.secret().getBytes(StandardCharsets.UTF_8)));
        return token.serialize();
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
