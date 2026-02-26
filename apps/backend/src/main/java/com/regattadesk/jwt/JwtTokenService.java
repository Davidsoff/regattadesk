package com.regattadesk.jwt;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for issuing and validating JWT tokens for anonymous public sessions.
 * 
 * This service implements the JWT lifecycle with:
 * - HS256 signing with kid in header
 * - Key rotation support with multiple active keys
 * - 5-day TTL with 20% refresh window (idempotent)
 * - Session ID (sid) claim for SSE connection tracking
 */
@ApplicationScoped
public class JwtTokenService {
    private static final Logger LOG = Logger.getLogger(JwtTokenService.class);
    
    private final JwtConfig config;
    private final JwtKeyRegistry keyRegistry;
    private final MACSigner signer;
    private final Map<String, MACVerifier> verifiers;
    
    @Inject
    public JwtTokenService(JwtConfig config, JwtKeyRegistry keyRegistry) {
        this.config = config;
        this.keyRegistry = keyRegistry;
        
        try {
            // Get the newest key for signing
            JwtKeyRegistry.KeyEntry newestKey = keyRegistry.getNewestKey();
            this.signer = new MACSigner(newestKey.secret());
            
            // Create verifiers for all active keys
            this.verifiers = new HashMap<>();
            for (JwtKeyRegistry.KeyEntry key : keyRegistry.getActiveKeys()) {
                this.verifiers.put(key.kid(), new MACVerifier(key.secret()));
            }
            
            LOG.infof("JWT service initialized with %d active keys", verifiers.size());
        } catch (JOSEException e) {
            throw new IllegalStateException("Invalid JWT key configuration", e);
        }
    }
    
    /**
     * Constructor for backward compatibility with tests that use JwtConfig only.
     * Creates a single-key registry from the config.
     * 
     * @deprecated Use constructor with JwtKeyRegistry
     */
    @Deprecated(forRemoval = false)
    JwtTokenService(JwtConfig config) {
        this.config = config;
        this.keyRegistry = null;
        
        try {
            byte[] secret = config.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (secret.length < 32) {
                throw new IllegalArgumentException(
                    "JWT secret must be at least 256 bits (32 bytes) for HS256"
                );
            }
            this.signer = new MACSigner(secret);
            
            // Single key verifier
            this.verifiers = new HashMap<>();
            this.verifiers.put(config.kid(), new MACVerifier(secret));
        } catch (JOSEException e) {
            throw new IllegalStateException("Invalid JWT secret configuration", e);
        }
    }
    
    /**
     * Issues a new anonymous session JWT token.
     * Token is signed with the newest active key.
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
        
        // Use the newest key's kid for signing
        String kid = keyRegistry != null ? keyRegistry.getNewestKey().kid() : config.kid();
        
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
            .keyID(kid)
            .build();
        
        SignedJWT signedJWT = new SignedJWT(header, claims);
        
        try {
            signedJWT.sign(signer);
            LOG.debugf("Issued anonymous session JWT token with kid=%s", kid);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign JWT token", e);
        }
    }
    
    /**
     * Validates a JWT token and returns the parsed token.
     * Verifies signature against all active keys.
     * 
     * @param token the JWT token string
     * @return the validated parsed JWT token
     * @throws InvalidTokenException if the token is invalid or expired
     */
    public ValidatedToken validateToken(String token) throws InvalidTokenException {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            
            // Get the kid from the token header
            String kid = signedJWT.getHeader().getKeyID();
            
            // Try to verify with the appropriate key
            MACVerifier verifier = verifiers.get(kid);
            if (verifier == null) {
                LOG.warnf("Rejected JWT token with unknown kid: %s", kid);
                throw new InvalidTokenException("Invalid JWT signature: unknown key ID");
            }
            
            // Verify signature
            if (!signedJWT.verify(verifier)) {
                LOG.warnf("Rejected JWT token with kid=%s due to invalid signature", kid);
                throw new InvalidTokenException("Invalid JWT signature");
            }
            
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            
            // Check expiration
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.before(new Date())) {
                LOG.warnf("Rejected JWT token with kid=%s due to missing or expired exp claim", kid);
                throw new InvalidTokenException("Token has expired");
            }

            String sessionId = claims.getStringClaim("sid");
            if (sessionId == null || sessionId.isBlank()) {
                LOG.warnf("Rejected JWT token with kid=%s missing sid claim", kid);
                throw new InvalidTokenException("Token missing required sid claim");
            }

            Date issueTime = claims.getIssueTime();
            if (issueTime == null) {
                LOG.warnf("Rejected JWT token with kid=%s missing iat claim", kid);
                throw new InvalidTokenException("Token missing required iat claim");
            }
            
            LOG.debugf("Successfully validated JWT token with kid=%s, sid=%s", kid, sessionId);
            
            return new ValidatedToken(
                sessionId,
                issueTime.toInstant(),
                expiration.toInstant()
            );
        } catch (ParseException | JOSEException e) {
            LOG.warn("Failed to parse or verify JWT token", e);
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
        boolean inRefreshWindow = !token.expiresAt().isAfter(refreshThreshold);
        if (inRefreshWindow) {
            LOG.debugf("Token for sid %s is in refresh window", token.sessionId());
        }
        return inRefreshWindow;
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
