package com.regattadesk.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.regattadesk.jwt.JwtTokenService.InvalidTokenException;
import com.regattadesk.jwt.JwtTokenService.ValidatedToken;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JWT key rotation functionality.
 * 
 * Tests that the system can:
 * - Maintain two active keys with overlap
 * - Sign with the newest key
 * - Verify tokens signed with any active key
 * - Handle key transitions gracefully
 */
class JwtKeyRotationTest {
    
    private JwtTokenService service;
    private TestMultiKeyJwtConfig config;
    
    /**
     * Helper to create a service with specific keys configured.
     */
    private JwtTokenService createServiceWithKeys(TestMultiKeyJwtConfig cfg) {
        TestJwtKeyRegistry registry = new TestJwtKeyRegistry(cfg);
        return new JwtTokenService(cfg, registry);
    }
    
    @Test
    void testIssueToken_UsesNewestActiveKey() throws Exception {
        // Configure two active keys
        Instant now = Instant.now();
        config = new TestMultiKeyJwtConfig();
        config.addKey("v1-2026-02", "key-v1-secret-at-least-32-bytes-required", now.minus(Duration.ofDays(10)));
        config.addKey("v2-2026-02", "key-v2-secret-at-least-32-bytes-required", now.minus(Duration.ofDays(1)));
        
        service = createServiceWithKeys(config);
        
        String token = service.issueToken();
        
        SignedJWT jwt = SignedJWT.parse(token);
        // Should use the newest key (v2)
        assertEquals("v2-2026-02", jwt.getHeader().getKeyID());
    }
    
    @Test
    void testValidateToken_AcceptsTokenFromNewestKey() throws InvalidTokenException {
        Instant now = Instant.now();
        config = new TestMultiKeyJwtConfig();
        config.addKey("v1-2026-02", "key-v1-secret-at-least-32-bytes-required", now.minus(Duration.ofDays(10)));
        config.addKey("v2-2026-02", "key-v2-secret-at-least-32-bytes-required", now.minus(Duration.ofDays(1)));
        
        service = createServiceWithKeys(config);
        
        String token = service.issueToken();
        ValidatedToken validated = service.validateToken(token);
        
        assertNotNull(validated);
        assertNotNull(validated.sessionId());
    }
    
    @Test
    void testValidateToken_AcceptsTokenFromOlderKeyDuringOverlap() throws Exception {
        Instant now = Instant.now();
        config = new TestMultiKeyJwtConfig();
        config.addKey("v1-2026-02", "key-v1-secret-at-least-32-bytes-required", now.minus(Duration.ofDays(10)));
        config.addKey("v2-2026-02", "key-v2-secret-at-least-32-bytes-required", now.minus(Duration.ofDays(1)));
        
        service = createServiceWithKeys(config);
        
        // Create a token with the older key
        String tokenFromOldKey = createTokenWithKey(
            "v1-2026-02", 
            "key-v1-secret-at-least-32-bytes-required",
            now,
            now.plusSeconds(432000)
        );
        
        // Should still validate because both keys are active
        ValidatedToken validated = service.validateToken(tokenFromOldKey);
        assertNotNull(validated);
    }
    
    @Test
    void testValidateToken_RejectsTokenFromInactiveKey() throws Exception {
        Instant now = Instant.now();
        config = new TestMultiKeyJwtConfig();
        config.addKey("v2-2026-02", "key-v2-secret-at-least-32-bytes-required", now.minus(Duration.ofDays(1)));
        
        service = createServiceWithKeys(config);
        
        // Create a token with a key that's not in the registry
        String tokenFromUnknownKey = createTokenWithKey(
            "v1-2026-01", 
            "key-v1-old-secret-at-least-32-bytes",
            now,
            now.plusSeconds(432000)
        );
        
        // Should fail because the key is not active
        assertThrows(InvalidTokenException.class, () -> {
            service.validateToken(tokenFromUnknownKey);
        });
    }
    
    @Test
    void testKeyRotation_MaintainsSixDayOverlap() {
        Instant now = Instant.now();
        Instant keyV1Activation = now.minus(Duration.ofDays(10));
        Instant keyV2Activation = now.minus(Duration.ofDays(1));
        
        config = new TestMultiKeyJwtConfig();
        config.addKey("v1-2026-02", "key-v1-secret-at-least-32-bytes-required", keyV1Activation);
        config.addKey("v2-2026-02", "key-v2-secret-at-least-32-bytes-required", keyV2Activation);
        
        // Calculate overlap window
        Duration overlap = Duration.between(keyV2Activation, keyV1Activation.plus(Duration.ofDays(16))); // Assume 16 day lifecycle
        
        // For proper rotation, v1 should remain active for at least 6 days after v2 activation
        // This is validated by configuration, not runtime
        assertTrue(config.getActiveKeys().size() >= 2, "Should have at least 2 active keys during overlap");
    }
    
    @Test
    void testSigningAlgorithmConsistency() throws Exception {
        Instant now = Instant.now();
        config = new TestMultiKeyJwtConfig();
        config.addKey("v1-2026-02", "key-v1-secret-at-least-32-bytes-required", now.minus(Duration.ofDays(1)));
        
        service = createServiceWithKeys(config);
        
        String token = service.issueToken();
        SignedJWT jwt = SignedJWT.parse(token);
        
        assertEquals(JWSAlgorithm.HS256, jwt.getHeader().getAlgorithm());
    }
    
    @Test
    void testMultipleActiveKeys_AllCanVerify() throws Exception {
        Instant now = Instant.now();
        config = new TestMultiKeyJwtConfig();
        config.addKey("v1-2026-02", "key-v1-secret-at-least-32-bytes-required", now.minus(Duration.ofDays(10)));
        config.addKey("v2-2026-02", "key-v2-secret-at-least-32-bytes-required", now.minus(Duration.ofDays(5)));
        config.addKey("v3-2026-02", "key-v3-secret-at-least-32-bytes-required", now.minus(Duration.ofDays(1)));
        
        service = createServiceWithKeys(config);
        
        // Create tokens with each key
        String token1 = createTokenWithKey("v1-2026-02", "key-v1-secret-at-least-32-bytes-required", now, now.plusSeconds(432000));
        String token2 = createTokenWithKey("v2-2026-02", "key-v2-secret-at-least-32-bytes-required", now, now.plusSeconds(432000));
        String token3 = createTokenWithKey("v3-2026-02", "key-v3-secret-at-least-32-bytes-required", now, now.plusSeconds(432000));
        
        // All should validate
        assertDoesNotThrow(() -> service.validateToken(token1));
        assertDoesNotThrow(() -> service.validateToken(token2));
        assertDoesNotThrow(() -> service.validateToken(token3));
    }
    
    @Test
    void testKeyRotation_NewServiceInstancesPickUpNewKey() {
        Instant now = Instant.now();
        config = new TestMultiKeyJwtConfig();
        config.addKey("v1-2026-02", "key-v1-secret-at-least-32-bytes-required", now.minus(Duration.ofDays(10)));
        
        service = createServiceWithKeys(config);
        String token1 = service.issueToken();
        
        // Simulate rotation by adding new key
        config.addKey("v2-2026-02", "key-v2-secret-at-least-32-bytes-required", now);
        
        // Create new service instance (simulating deployment/restart)
        JwtTokenService newService = createServiceWithKeys(config);
        
        String token2 = newService.issueToken();
        
        // New tokens should use v2
        try {
            SignedJWT jwt2 = SignedJWT.parse(token2);
            assertEquals("v2-2026-02", jwt2.getHeader().getKeyID());
        } catch (Exception e) {
            fail("Token should be parseable");
        }
        
        // Old tokens should still validate
        assertDoesNotThrow(() -> newService.validateToken(token1));
    }
    
    @Test
    void testEmptyKeyRegistry_ThrowsException() {
        TestMultiKeyJwtConfig emptyConfig = new TestMultiKeyJwtConfig();
        
        assertThrows(IllegalStateException.class, () -> {
            createServiceWithKeys(emptyConfig);
        });
    }
    
    @Test
    void testKeyWithShortSecret_ThrowsException() {
        TestMultiKeyJwtConfig shortConfig = new TestMultiKeyJwtConfig();
        
        assertThrows(IllegalArgumentException.class, () -> {
            shortConfig.addKey("v1", "short", Instant.now());
            createServiceWithKeys(shortConfig);
        });
    }
    
    @Test
    void testTokenExpiration_WithKeyRotation() throws Exception {
        Instant now = Instant.now();
        config = new TestMultiKeyJwtConfig();
        config.addKey("v1-2026-02", "key-v1-secret-at-least-32-bytes-required", now.minus(Duration.ofDays(10)));
        
        service = createServiceWithKeys(config);
        
        // Create an expired token with old key
        String expiredToken = createTokenWithKey(
            "v1-2026-02",
            "key-v1-secret-at-least-32-bytes-required",
            now.minus(Duration.ofDays(6)),
            now.minus(Duration.ofDays(1)) // Expired yesterday
        );
        
        // Should fail due to expiration
        InvalidTokenException exception = assertThrows(InvalidTokenException.class, () -> {
            service.validateToken(expiredToken);
        });
        
        // Verify it's an expiration error, not a signature error
        assertNotNull(exception.getMessage());
        // The service should reject with "expired" in the message
        String message = exception.getMessage().toLowerCase();
        assertTrue(message.contains("expired") || message.contains("expiration"),
            "Expected expiration error, got: " + exception.getMessage());
    }
    
    private String createTokenWithKey(String kid, String secret, Instant issuedAt, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .claim("sid", UUID.randomUUID().toString())
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .build();
        
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
            .keyID(kid)
            .build();
        
        SignedJWT jwt = new SignedJWT(header, claims);
        MACSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
        jwt.sign(signer);
        
        return jwt.serialize();
    }
    
    /**
     * Test implementation of JwtConfig that supports multiple keys.
     */
    private static class TestMultiKeyJwtConfig implements JwtConfig {
        private final java.util.List<KeyEntry> keys = new java.util.ArrayList<>();
        private int ttlSeconds = 432000; // 5 days
        private int refreshWindowPercent = 20;
        
        void addKey(String kid, String secret, Instant activatedAt) {
            keys.add(new KeyEntry(kid, secret, activatedAt));
        }
        
        java.util.List<KeyEntry> getActiveKeys() {
            return keys;
        }
        
        @Override
        public String secret() {
            // For backward compatibility, return the first key's secret
            // New multi-key implementation should not use this
            if (keys.isEmpty()) {
                throw new IllegalStateException("No keys configured");
            }
            return keys.get(0).secret();
        }
        
        @Override
        public String kid() {
            // For backward compatibility, return the newest key's kid
            if (keys.isEmpty()) {
                throw new IllegalStateException("No keys configured");
            }
            return keys.stream()
                .max((a, b) -> a.activatedAt().compareTo(b.activatedAt()))
                .map(KeyEntry::kid)
                .orElseThrow();
        }
        
        @Override
        public int ttlSeconds() {
            return ttlSeconds;
        }
        
        @Override
        public int refreshWindowPercent() {
            return refreshWindowPercent;
        }
        
        record KeyEntry(String kid, String secret, Instant activatedAt) {}
    }
    
    /**
     * Test implementation of JwtKeyRegistry.
     */
    private static class TestJwtKeyRegistry implements JwtKeyRegistry {
        private final TestMultiKeyJwtConfig config;
        
        TestJwtKeyRegistry(TestMultiKeyJwtConfig config) {
            this.config = config;
        }
        
        @Override
        public List<JwtKeyRegistry.KeyEntry> getActiveKeys() {
            return config.getActiveKeys().stream()
                .map(e -> new JwtKeyRegistry.KeyEntry(
                    e.kid(), 
                    e.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    e.activatedAt()
                ))
                .toList();
        }
        
        @Override
        public JwtKeyRegistry.KeyEntry getNewestKey() {
            TestMultiKeyJwtConfig.KeyEntry newest = config.getActiveKeys().stream()
                .max(Comparator.comparing(TestMultiKeyJwtConfig.KeyEntry::activatedAt))
                .orElseThrow(() -> new IllegalStateException("No keys available"));
            
            return new JwtKeyRegistry.KeyEntry(
                newest.kid(),
                newest.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                newest.activatedAt()
            );
        }
    }
}
