package com.regattadesk.jwt;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for JWT public anonymous session tokens.
 */
@ConfigMapping(prefix = "jwt.public")
public interface JwtConfig {
    
    /**
     * The secret key used for signing JWT tokens (HS256).
     * Must be at least 256 bits (32 bytes) for HS256.
     */
    String secret();
    
    /**
     * The key ID (kid) to include in the JWT header.
     */
    String kid();
    
    /**
     * The TTL (time-to-live) in seconds for the JWT token.
     * Default is 5 days (432000 seconds).
     */
    @WithDefault("432000")
    int ttlSeconds();
    
    /**
     * The refresh window as a percentage of the TTL.
     * Default is 20% (refresh when token has less than 20% of TTL remaining).
     */
    @WithDefault("20")
    int refreshWindowPercent();
}
