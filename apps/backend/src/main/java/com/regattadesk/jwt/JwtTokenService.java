package com.regattadesk.jwt;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Service for issuing and validating JWT tokens for anonymous public sessions.
 * 
 * This service implements the JWT lifecycle with:
 * - HS256 signing with kid in header
 * - 5-day TTL with 20% refresh window (idempotent)
 * - Session ID (sid) claim for SSE connection tracking
 */
@ApplicationScoped
public class JwtTokenService {
    
    private final JwtConfig config;
    private final MACSigner signer;
    private final MACVerifier verifier;
    
    @Inject
    public JwtTokenService(JwtConfig config) {
        this.config = config;
        try {
            byte[] secret = config.secret().getBytes();
            if (secret.length < 32) {
                throw new IllegalArgumentException(
                    "JWT secret must be at least 256 bits (32 bytes) for HS256"
                );
            }
            this.signer = new MACSigner(secret);
            this.verifier = new MACVerifier(secret);
        } catch (JOSEException e) {
            throw new IllegalStateException("Invalid JWT secret configuration", e);
        }
    }
    
    /**
     * Issues a new anonymous session JWT token.
     * 
     * @return the signed JWT token string
     */
    public String issueToken() {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(config.ttlSeconds());
        
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .claim("sid", UUID.randomUUID().toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiration))
            .build();
        
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
            .keyID(config.kid())
            .build();
        
        SignedJWT signedJWT = new SignedJWT(header, claims);
        
        try {
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign JWT token", e);
        }
    }
    
    /**
     * Validates a JWT token and returns the parsed token.
     * 
     * @param token the JWT token string
     * @return the validated parsed JWT token
     * @throws InvalidTokenException if the token is invalid or expired
     */
    public ValidatedToken validateToken(String token) throws InvalidTokenException {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            
            // Verify signature
            if (!signedJWT.verify(verifier)) {
                throw new InvalidTokenException("Invalid JWT signature");
            }
            
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            
            // Check expiration
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.before(new Date())) {
                throw new InvalidTokenException("Token has expired");
            }
            
            return new ValidatedToken(
                claims.getStringClaim("sid"),
                claims.getIssueTime().toInstant(),
                expiration.toInstant()
            );
        } catch (ParseException | JOSEException e) {
            throw new InvalidTokenException("Failed to parse or verify JWT token", e);
        }
    }
    
    /**
     * Checks if a token is within the refresh window.
     * The refresh window is the last 20% of the token's TTL.
     * 
     * @param token the validated token
     * @return true if the token should be refreshed, false otherwise
     */
    public boolean isInRefreshWindow(ValidatedToken token) {
        Instant now = Instant.now();
        long ttlSeconds = config.ttlSeconds();
        long refreshWindowSeconds = (ttlSeconds * config.refreshWindowPercent()) / 100;
        
        // Token is in refresh window if it expires within the refresh window duration
        Instant refreshThreshold = now.plusSeconds(refreshWindowSeconds);
        return token.expiresAt().isBefore(refreshThreshold) || token.expiresAt().equals(refreshThreshold);
    }
    
    /**
     * Represents a validated JWT token with extracted claims.
     */
    public record ValidatedToken(
        String sessionId,
        Instant issuedAt,
        Instant expiresAt
    ) {}
    
    /**
     * Exception thrown when a JWT token is invalid.
     */
    public static class InvalidTokenException extends Exception {
        public InvalidTokenException(String message) {
            super(message);
        }
        
        public InvalidTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
