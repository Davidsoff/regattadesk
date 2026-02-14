package com.regattadesk.operator;

import com.regattadesk.operator.events.OperatorTokenIssuedEvent;
import com.regattadesk.operator.events.OperatorTokenRevokedEvent;
import com.regattadesk.operator.events.OperatorTokenValidatedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for operator token lifecycle management.
 * 
 * Provides high-level operations for issuing, validating, and revoking operator tokens.
 * Integrates with the repository layer and validation logic.
 */
@ApplicationScoped
public class OperatorTokenService {
    
    private static final int MAX_TOKEN_GENERATION_ATTEMPTS = 10;
    private static final int TOKEN_LENGTH_BYTES = 32;
    private static final int PIN_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();
    
    private final OperatorTokenRepository repository;
    private final OperatorTokenValidator validator;
    
    @Inject
    public OperatorTokenService(OperatorTokenRepository repository) {
        this(repository, new OperatorTokenValidator());
    }

    OperatorTokenService(OperatorTokenRepository repository, OperatorTokenValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }
    
    /**
     * Issues a new operator token.
     * 
     * @param regattaId the regatta scope
     * @param blockId the optional block scope
     * @param station the station identifier
     * @param validFrom the validity start time
     * @param validUntil the validity end time
     * @return the newly issued token
     * @throws IllegalArgumentException if parameters are invalid
     */
    public OperatorToken issueToken(
            UUID regattaId,
            UUID blockId,
            String station,
            Instant validFrom,
            Instant validUntil) {
        
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        if (station == null || station.isBlank()) {
            throw new IllegalArgumentException("Station cannot be null or blank");
        }
        if (validFrom == null) {
            throw new IllegalArgumentException("Valid from cannot be null");
        }
        if (validUntil == null) {
            throw new IllegalArgumentException("Valid until cannot be null");
        }
        if (!validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("Valid until must be after valid from");
        }
        
        Instant now = Instant.now();
        for (int attempt = 1; attempt <= MAX_TOKEN_GENERATION_ATTEMPTS; attempt++) {
            String tokenString = generateTokenString();
            while (repository.existsByToken(tokenString)) {
                tokenString = generateTokenString();
            }

            OperatorToken token = new OperatorToken(
                UUID.randomUUID(),
                regattaId,
                blockId,
                station,
                tokenString,
                generatePin(),
                validFrom,
                validUntil,
                true,
                now,
                now
            );

            try {
                OperatorToken saved = repository.save(token);
                repository.appendEvent(new OperatorTokenIssuedEvent(
                    saved.getId(),
                    saved.getRegattaId(),
                    saved.getBlockId(),
                    saved.getStation(),
                    saved.getValidFrom(),
                    saved.getValidUntil(),
                    now,
                    "system"
                ));
                return saved;
            } catch (RuntimeException e) {
                if (!isUniqueConstraintViolation(e) || attempt == MAX_TOKEN_GENERATION_ATTEMPTS) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Failed to generate a unique token after retries");
    }
    
    /**
     * Validates a token by its token string.
     * 
     * @param tokenString the token string to validate
     * @param regattaId the regatta ID to check against
     * @param station the station identifier to check against
     * @param blockId the optional block ID to check against
     * @return the validation result
     */
    public TokenValidationResult validateToken(
            String tokenString,
            UUID regattaId,
            String station,
            UUID blockId) {
        
        if (tokenString == null || tokenString.isBlank()) {
            TokenValidationResult result = new TokenValidationResult(
                null,
                OperatorTokenValidator.ValidationResult.INVALID_TOKEN,
                "Token string is required"
            );
            appendValidationEvent(
                UUID.nameUUIDFromBytes("invalid:blank-token".getBytes(StandardCharsets.UTF_8)),
                regattaId,
                station,
                result,
                Instant.now()
            );
            return result;
        }
        if (regattaId == null) {
            TokenValidationResult result = new TokenValidationResult(
                null,
                OperatorTokenValidator.ValidationResult.INVALID_TOKEN,
                "Regatta ID is required"
            );
            appendValidationEvent(null, null, station, result, Instant.now());
            return result;
        }
        if (station == null || station.isBlank()) {
            TokenValidationResult result = new TokenValidationResult(
                null,
                OperatorTokenValidator.ValidationResult.INVALID_TOKEN,
                "Station is required"
            );
            appendValidationEvent(
                UUID.nameUUIDFromBytes(("invalid:station:" + tokenString).getBytes(StandardCharsets.UTF_8)),
                regattaId,
                null,
                result,
                Instant.now()
            );
            return result;
        }
        
        Optional<OperatorToken> tokenOpt = repository.findByToken(tokenString);
        if (tokenOpt.isEmpty()) {
            TokenValidationResult result = new TokenValidationResult(
                null,
                OperatorTokenValidator.ValidationResult.NOT_FOUND,
                "Token not found"
            );
            appendValidationEvent(
                UUID.nameUUIDFromBytes(("missing:" + tokenString).getBytes(StandardCharsets.UTF_8)),
                regattaId,
                station,
                result,
                Instant.now()
            );
            return result;
        }
        
        OperatorToken token = tokenOpt.get();
        OperatorTokenValidator.ValidationResult result;
        try {
            result = validator.validateFull(
                token,
                Instant.now(),
                regattaId,
                station,
                blockId
            );
        } catch (IllegalArgumentException e) {
            return new TokenValidationResult(
                token,
                OperatorTokenValidator.ValidationResult.INVALID_TOKEN,
                e.getMessage()
            );
        }
        TokenValidationResult tokenValidationResult = new TokenValidationResult(token, result, result.getMessage());
        appendValidationEvent(token.getId(), regattaId, station, tokenValidationResult, Instant.now());
        return tokenValidationResult;
    }
    
    /**
     * Revokes a token by its ID.
     * 
     * @param tokenId the token ID
     * @return true if the token was revoked, false if not found
     */
    public boolean revokeToken(UUID tokenId) {
        if (tokenId == null) {
            throw new IllegalArgumentException("Token ID cannot be null");
        }
        
        return revokeTokenForRegatta(tokenId, null) == RevokeResult.REVOKED;
    }

    public RevokeResult revokeTokenForRegatta(UUID tokenId, UUID regattaId) {
        if (tokenId == null) {
            throw new IllegalArgumentException("Token ID cannot be null");
        }

        Optional<OperatorToken> tokenOpt = repository.findById(tokenId);
        if (tokenOpt.isEmpty()) {
            return RevokeResult.NOT_FOUND;
        }

        OperatorToken token = tokenOpt.get();
        if (regattaId != null && !token.getRegattaId().equals(regattaId)) {
            return RevokeResult.NOT_FOUND;
        }

        boolean revoked = repository.revoke(tokenId);
        if (revoked) {
            repository.appendEvent(new OperatorTokenRevokedEvent(
                token.getId(),
                token.getRegattaId(),
                token.getStation(),
                "revoked",
                Instant.now(),
                "system"
            ));
            return RevokeResult.REVOKED;
        }

        return RevokeResult.NOT_FOUND;
    }
    
    /**
     * Gets a token by its ID.
     * 
     * @param tokenId the token ID
     * @return the token if found
     */
    public Optional<OperatorToken> getTokenById(UUID tokenId) {
        if (tokenId == null) {
            throw new IllegalArgumentException("Token ID cannot be null");
        }
        
        return repository.findById(tokenId);
    }
    
    /**
     * Gets a token by its token string.
     * 
     * @param tokenString the token string
     * @return the token if found
     */
    public Optional<OperatorToken> getTokenByString(String tokenString) {
        if (tokenString == null || tokenString.isBlank()) {
            return Optional.empty();
        }
        
        return repository.findByToken(tokenString);
    }
    
    /**
     * Lists all tokens for a regatta.
     * 
     * @param regattaId the regatta ID
     * @return list of tokens
     */
    public List<OperatorToken> listTokens(UUID regattaId) {
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        
        return repository.findByRegattaId(regattaId);
    }
    
    /**
     * Lists active tokens for a specific station.
     * 
     * @param regattaId the regatta ID
     * @param station the station identifier
     * @return list of active tokens
     */
    public List<OperatorToken> listActiveTokensForStation(UUID regattaId, String station) {
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        if (station == null || station.isBlank()) {
            throw new IllegalArgumentException("Station cannot be null or blank");
        }
        
        return repository.findActiveByRegattaIdAndStation(regattaId, station);
    }
    
    /**
     * Lists all currently valid tokens for a regatta.
     * 
     * @param regattaId the regatta ID
     * @return list of valid tokens
     */
    public List<OperatorToken> listValidTokens(UUID regattaId) {
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        
        return repository.findValidTokens(regattaId, Instant.now());
    }
    
    /**
     * Generates a secure random token string.
     */
    private String generateTokenString() {
        byte[] tokenBytes = new byte[TOKEN_LENGTH_BYTES];
        RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
    
    /**
     * Generates a random numeric PIN.
     */
    private String generatePin() {
        int pin = RANDOM.nextInt((int) Math.pow(10, PIN_LENGTH));
        return String.format("%0" + PIN_LENGTH + "d", pin);
    }

    private boolean isUniqueConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("23505") || message.toLowerCase().contains("unique"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void appendValidationEvent(
            UUID tokenId,
            UUID regattaId,
            String station,
            TokenValidationResult result,
            Instant occurredAt) {
        if (tokenId == null || regattaId == null) {
            return;
        }
        repository.appendEvent(new OperatorTokenValidatedEvent(
            tokenId,
            regattaId,
            station,
            result.result.isValid(),
            result.message,
            occurredAt,
            "system"
        ));
    }

    public enum RevokeResult {
        REVOKED,
        NOT_FOUND
    }
    
    /**
     * Result of a token validation operation.
     */
    public static class TokenValidationResult {
        private final OperatorToken token;
        private final OperatorTokenValidator.ValidationResult result;
        private final String message;
        
        public TokenValidationResult(
                OperatorToken token,
                OperatorTokenValidator.ValidationResult result,
                String message) {
            this.token = token;
            this.result = result;
            this.message = message;
        }
        
        public Optional<OperatorToken> getToken() {
            return Optional.ofNullable(token);
        }
        
        public OperatorTokenValidator.ValidationResult getResult() {
            return result;
        }
        
        public String getMessage() {
            return message;
        }
        
        public boolean isValid() {
            return result.isValid();
        }
    }
}
