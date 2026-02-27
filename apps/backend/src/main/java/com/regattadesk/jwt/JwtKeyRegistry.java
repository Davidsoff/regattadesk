package com.regattadesk.jwt;

import java.time.Instant;
import java.util.List;

/**
 * Registry for JWT signing keys with rotation support.
 * 
 * Maintains multiple active keys to enable safe key rotation with overlap.
 * Keys are identified by kid (key ID) and have activation timestamps.
 * 
 * For v0.1, keys are configured via application properties.
 * External KMS integration is out of scope.
 */
public interface JwtKeyRegistry {
    
    /**
     * Get all currently active keys.
     * Keys should be ordered by activation time (oldest first).
     * 
     * @return list of active keys, never empty
     */
    List<KeyEntry> getActiveKeys();
    
    /**
     * Get the newest active key for signing new tokens.
     * 
     * @return the newest key entry
     */
    KeyEntry getNewestKey();
    
    /**
     * Represents a single JWT signing key with metadata.
     * 
     * @param kid The key identifier to include in JWT header
     * @param secret The secret key bytes (must be at least 256 bits for HS256)
     * @param activatedAt When this key became active
     */
    record KeyEntry(String kid, byte[] secret, Instant activatedAt) {
        
        public KeyEntry {
            if (kid == null || kid.isBlank()) {
                throw new IllegalArgumentException("Key ID (kid) must not be null or blank");
            }
            if (secret == null || secret.length < 32) {
                throw new IllegalArgumentException(
                    "Secret must be at least 256 bits (32 bytes) for HS256"
                );
            }
            if (activatedAt == null) {
                throw new IllegalArgumentException("Activation time must not be null");
            }
        }
    }
}
