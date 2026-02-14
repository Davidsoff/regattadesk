package com.regattadesk.operator;

import com.regattadesk.operator.events.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for station handoff lifecycle management.
 * 
 * Provides operations for requesting, revealing PIN, completing, and cancelling
 * station handoffs between operator devices.
 */
@ApplicationScoped
public class StationHandoffService {
    
    private static final int PIN_LENGTH = 6;
    private static final int DEFAULT_HANDOFF_TTL_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    
    private final StationHandoffRepository repository;
    private final OperatorTokenService tokenService;
    
    @Inject
    public StationHandoffService(StationHandoffRepository repository, OperatorTokenService tokenService) {
        this.repository = repository;
        this.tokenService = tokenService;
    }
    
    /**
     * Requests a new station handoff.
     * 
     * @param regattaId the regatta ID
     * @param tokenId the token ID
     * @param station the station identifier
     * @param requestingDeviceId the device requesting handoff
     * @return the created handoff
     * @throws IllegalArgumentException if parameters are invalid
     * @throws IllegalStateException if token is not found or invalid
     */
    public StationHandoff requestHandoff(
            UUID regattaId,
            UUID tokenId,
            String station,
            String requestingDeviceId) {
        
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        if (tokenId == null) {
            throw new IllegalArgumentException("Token ID cannot be null");
        }
        if (station == null || station.isBlank()) {
            throw new IllegalArgumentException("Station cannot be null or blank");
        }
        if (requestingDeviceId == null || requestingDeviceId.isBlank()) {
            throw new IllegalArgumentException("Requesting device ID cannot be null or blank");
        }
        
        // Verify token exists and is valid
        Optional<OperatorToken> tokenOpt = tokenService.getTokenById(tokenId);
        if (tokenOpt.isEmpty()) {
            throw new IllegalStateException("Token not found");
        }
        
        OperatorToken token = tokenOpt.get();
        if (!token.getRegattaId().equals(regattaId)) {
            throw new IllegalStateException("Token does not belong to regatta");
        }
        if (!token.getStation().equals(station)) {
            throw new IllegalStateException("Token station does not match");
        }
        if (!token.isCurrentlyValid()) {
            throw new IllegalStateException("Token is not currently valid");
        }
        
        Instant now = Instant.now();
        Instant expiresAt = now.plus(DEFAULT_HANDOFF_TTL_MINUTES, ChronoUnit.MINUTES);
        
        StationHandoff handoff = new StationHandoff(
            UUID.randomUUID(),
            regattaId,
            tokenId,
            station,
            requestingDeviceId,
            generatePin(),
            StationHandoff.HandoffStatus.PENDING,
            now,
            expiresAt,
            null
        );
        
        StationHandoff saved = repository.save(handoff);
        
        repository.appendEvent(new StationHandoffRequestedEvent(
            saved.getId(),
            saved.getRegattaId(),
            saved.getTokenId(),
            saved.getStation(),
            saved.getRequestingDeviceId(),
            saved.getExpiresAt(),
            now,
            "system"
        ));
        
        return saved;
    }
    
    /**
     * Gets a handoff by its ID.
     * 
     * @param handoffId the handoff ID
     * @return the handoff if found
     */
    public Optional<StationHandoff> getHandoff(UUID handoffId) {
        if (handoffId == null) {
            throw new IllegalArgumentException("Handoff ID cannot be null");
        }
        return repository.findById(handoffId);
    }
    
    /**
     * Reveals the PIN for a handoff (operator or admin).
     * 
     * @param handoffId the handoff ID
     * @param revealedBy identifier of who revealed the PIN
     * @param isAdminReveal whether this is an admin reveal
     * @return the handoff with revealed PIN info
     * @throws IllegalStateException if handoff not found or not pending
     */
    public HandoffPinRevealResult revealPin(UUID handoffId, String revealedBy, boolean isAdminReveal) {
        if (handoffId == null) {
            throw new IllegalArgumentException("Handoff ID cannot be null");
        }
        if (revealedBy == null || revealedBy.isBlank()) {
            throw new IllegalArgumentException("Revealed by cannot be null or blank");
        }
        
        Optional<StationHandoff> handoffOpt = repository.findById(handoffId);
        if (handoffOpt.isEmpty()) {
            throw new IllegalStateException("Handoff not found");
        }
        
        StationHandoff handoff = handoffOpt.get();
        
        if (!handoff.isPending()) {
            throw new IllegalStateException("Handoff is not pending");
        }
        
        if (handoff.isExpired()) {
            throw new IllegalStateException("Handoff has expired");
        }
        
        Instant now = Instant.now();
        repository.appendEvent(new StationHandoffPinRevealedEvent(
            handoff.getId(),
            handoff.getRegattaId(),
            handoff.getStation(),
            revealedBy,
            isAdminReveal,
            now,
            revealedBy
        ));
        
        return new HandoffPinRevealResult(handoff, handoff.getPin());
    }
    
    /**
     * Completes a handoff with PIN verification.
     * 
     * @param handoffId the handoff ID
     * @param providedPin the PIN provided by the requesting device
     * @return result of the completion attempt
     */
    public HandoffCompletionResult completeHandoff(UUID handoffId, String providedPin) {
        if (handoffId == null) {
            throw new IllegalArgumentException("Handoff ID cannot be null");
        }
        if (providedPin == null || providedPin.isBlank()) {
            return new HandoffCompletionResult(false, "PIN is required", null);
        }
        
        Optional<StationHandoff> handoffOpt = repository.findById(handoffId);
        if (handoffOpt.isEmpty()) {
            return new HandoffCompletionResult(false, "Handoff not found", null);
        }
        
        StationHandoff handoff = handoffOpt.get();
        
        if (!handoff.isPending()) {
            return new HandoffCompletionResult(false, "Handoff is not pending", handoff);
        }
        
        if (handoff.isExpired()) {
            return new HandoffCompletionResult(false, "Handoff has expired", handoff);
        }
        
        if (!handoff.getPin().equals(providedPin)) {
            return new HandoffCompletionResult(false, "Invalid PIN", handoff);
        }
        
        Instant now = Instant.now();
        StationHandoff completedHandoff = new StationHandoff(
            handoff.getId(),
            handoff.getRegattaId(),
            handoff.getTokenId(),
            handoff.getStation(),
            handoff.getRequestingDeviceId(),
            handoff.getPin(),
            StationHandoff.HandoffStatus.COMPLETED,
            handoff.getCreatedAt(),
            handoff.getExpiresAt(),
            now
        );
        
        StationHandoff updated = repository.update(completedHandoff);
        
        repository.appendEvent(new StationHandoffCompletedEvent(
            updated.getId(),
            updated.getRegattaId(),
            updated.getTokenId(),
            updated.getStation(),
            updated.getRequestingDeviceId(),
            now,
            "system"
        ));
        
        return new HandoffCompletionResult(true, "Handoff completed successfully", updated);
    }
    
    /**
     * Cancels a pending handoff.
     * 
     * @param handoffId the handoff ID
     * @param reason the cancellation reason
     * @return true if cancelled, false if not found or not pending
     */
    public boolean cancelHandoff(UUID handoffId, String reason) {
        if (handoffId == null) {
            throw new IllegalArgumentException("Handoff ID cannot be null");
        }
        
        Optional<StationHandoff> handoffOpt = repository.findById(handoffId);
        if (handoffOpt.isEmpty()) {
            return false;
        }
        
        StationHandoff handoff = handoffOpt.get();
        
        if (!handoff.isPending()) {
            return false;
        }
        
        Instant now = Instant.now();
        StationHandoff cancelledHandoff = new StationHandoff(
            handoff.getId(),
            handoff.getRegattaId(),
            handoff.getTokenId(),
            handoff.getStation(),
            handoff.getRequestingDeviceId(),
            handoff.getPin(),
            StationHandoff.HandoffStatus.CANCELLED,
            handoff.getCreatedAt(),
            handoff.getExpiresAt(),
            now
        );
        
        repository.update(cancelledHandoff);
        
        repository.appendEvent(new StationHandoffCancelledEvent(
            handoff.getId(),
            handoff.getRegattaId(),
            handoff.getStation(),
            reason != null ? reason : "cancelled",
            now,
            "system"
        ));
        
        return true;
    }
    
    /**
     * Lists pending handoffs for a station.
     * 
     * @param regattaId the regatta ID
     * @param station the station identifier
     * @return list of pending handoffs
     */
    public List<StationHandoff> listPendingHandoffs(UUID regattaId, String station) {
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        if (station == null || station.isBlank()) {
            throw new IllegalArgumentException("Station cannot be null or blank");
        }
        
        return repository.findPendingByRegattaAndStation(regattaId, station);
    }
    
    /**
     * Generates a random numeric PIN.
     */
    private String generatePin() {
        int pin = RANDOM.nextInt((int) Math.pow(10, PIN_LENGTH));
        return String.format("%0" + PIN_LENGTH + "d", pin);
    }
    
    /**
     * Result of a PIN reveal operation.
     */
    public record HandoffPinRevealResult(StationHandoff handoff, String pin) {}
    
    /**
     * Result of a handoff completion operation.
     */
    public record HandoffCompletionResult(boolean success, String message, StationHandoff handoff) {}
}
