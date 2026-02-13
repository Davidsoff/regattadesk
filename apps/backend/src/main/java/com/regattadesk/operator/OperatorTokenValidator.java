package com.regattadesk.operator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Validator for operator token validity and scope checks.
 * 
 * Provides methods to validate tokens according to business rules:
 * - Active status (not revoked)
 * - Validity window (time-based)
 * - Regatta scope
 * - Station scope
 * - Optional block scope
 */
public final class OperatorTokenValidator {
    
    /**
     * Result of a token validation check.
     */
    public enum ValidationResult {
        VALID("Token is valid"),
        REVOKED("Token has been revoked"),
        EXPIRED("Token has expired"),
        NOT_YET_VALID("Token is not yet valid"),
        INVALID_REGATTA_SCOPE("Token is not valid for this regatta"),
        INVALID_STATION_SCOPE("Token is not valid for this station"),
        INVALID_BLOCK_SCOPE("Token is not valid for this block");
        
        private final String message;
        
        ValidationResult(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
        
        public boolean isValid() {
            return this == VALID;
        }
    }
    
    /**
     * Validates a token for general validity (active and within time window).
     * 
     * @param token the token to validate
     * @param checkTime the time to check validity at
     * @return the validation result
     */
    public ValidationResult validateToken(OperatorToken token, Instant checkTime) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        if (checkTime == null) {
            throw new IllegalArgumentException("Check time cannot be null");
        }
        
        if (!token.isActive()) {
            return ValidationResult.REVOKED;
        }
        
        if (checkTime.isBefore(token.getValidFrom())) {
            return ValidationResult.NOT_YET_VALID;
        }
        
        if (!checkTime.isBefore(token.getValidUntil())) {
            return ValidationResult.EXPIRED;
        }
        
        return ValidationResult.VALID;
    }
    
    /**
     * Validates a token for regatta scope.
     * 
     * @param token the token to validate
     * @param regattaId the regatta ID to check against
     * @return the validation result
     */
    public ValidationResult validateRegattaScope(OperatorToken token, UUID regattaId) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        
        if (!Objects.equals(token.getRegattaId(), regattaId)) {
            return ValidationResult.INVALID_REGATTA_SCOPE;
        }
        
        return ValidationResult.VALID;
    }
    
    /**
     * Validates a token for station scope.
     * 
     * @param token the token to validate
     * @param station the station identifier to check against
     * @return the validation result
     */
    public ValidationResult validateStationScope(OperatorToken token, String station) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        if (station == null || station.isBlank()) {
            throw new IllegalArgumentException("Station cannot be null or blank");
        }
        
        if (!Objects.equals(token.getStation(), station)) {
            return ValidationResult.INVALID_STATION_SCOPE;
        }
        
        return ValidationResult.VALID;
    }
    
    /**
     * Validates a token for block scope (if applicable).
     * 
     * @param token the token to validate
     * @param blockId the block ID to check against (can be null)
     * @return the validation result
     */
    public ValidationResult validateBlockScope(OperatorToken token, UUID blockId) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        
        // If token has no block scope, it's valid for any block
        if (!token.hasBlockScope()) {
            return ValidationResult.VALID;
        }
        
        // If token has block scope, check if it matches
        if (!Objects.equals(token.getBlockId(), blockId)) {
            return ValidationResult.INVALID_BLOCK_SCOPE;
        }
        
        return ValidationResult.VALID;
    }
    
    /**
     * Performs a full validation check including all scopes.
     * 
     * @param token the token to validate
     * @param checkTime the time to check validity at
     * @param regattaId the regatta ID to check against
     * @param station the station identifier to check against
     * @param blockId the block ID to check against (can be null)
     * @return the validation result (first failure or VALID)
     */
    public ValidationResult validateFull(
            OperatorToken token,
            Instant checkTime,
            UUID regattaId,
            String station,
            UUID blockId) {
        
        // Check basic validity first
        ValidationResult timeResult = validateToken(token, checkTime);
        if (!timeResult.isValid()) {
            return timeResult;
        }
        
        // Check regatta scope
        ValidationResult regattaResult = validateRegattaScope(token, regattaId);
        if (!regattaResult.isValid()) {
            return regattaResult;
        }
        
        // Check station scope
        ValidationResult stationResult = validateStationScope(token, station);
        if (!stationResult.isValid()) {
            return stationResult;
        }
        
        // Check block scope
        ValidationResult blockResult = validateBlockScope(token, blockId);
        if (!blockResult.isValid()) {
            return blockResult;
        }
        
        return ValidationResult.VALID;
    }
}
