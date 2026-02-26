package com.regattadesk.investigation;

import com.regattadesk.aggregate.AggregateRoot;
import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Investigation aggregate root (BC07-001).
 * 
 * Represents an investigation into an entry for potential rule violations
 * or adjudication matters. Manages investigation lifecycle and penalty application.
 * 
 * Lifecycle states:
 * - open: Investigation is active and can be modified
 * - closed: Investigation has been resolved with an outcome
 */
public class InvestigationAggregate extends AggregateRoot<InvestigationAggregate> {
    private UUID regattaId;
    private UUID entryId;
    private String description;
    private InvestigationOutcome outcome;
    private Integer penaltySeconds;
    private Instant closedAt;

    public InvestigationAggregate(UUID id) {
        super(id);
    }

    /**
     * Opens a new investigation for an entry.
     * 
     * @param investigationId unique ID for the investigation
     * @param regattaId the regatta this investigation belongs to
     * @param entryId the entry being investigated
     * @param description description of the issue being investigated
     * @return new investigation aggregate
     */
    public static InvestigationAggregate open(
            UUID investigationId,
            UUID regattaId,
            UUID entryId,
            String description
    ) {
        validateOpen(regattaId, entryId, description);
        
        var investigation = new InvestigationAggregate(investigationId);
        investigation.raiseEvent(new InvestigationOpenedEvent(
            investigationId,
            regattaId,
            entryId,
            description
        ));
        return investigation;
    }

    /**
     * Updates the investigation description.
     * 
     * @param newDescription updated description
     * @throws IllegalStateException if investigation is closed
     */
    public void updateDescription(String newDescription) {
        if (!isOpen()) {
            throw new IllegalStateException("Cannot update a closed investigation");
        }
        if (newDescription == null || newDescription.isBlank()) {
            throw new IllegalArgumentException("Description cannot be null or blank");
        }
        
        raiseEvent(new InvestigationUpdatedEvent(getId(), newDescription));
    }

    /**
     * Closes the investigation with an outcome.
     * 
     * @param outcome the outcome of the investigation
     * @param penaltySeconds penalty seconds (required for PENALTY outcome, must be null for others)
     * @throws IllegalStateException if investigation is already closed
     * @throws IllegalArgumentException if penalty validation fails
     */
    public void close(InvestigationOutcome outcome, Integer penaltySeconds) {
        if (!isOpen()) {
            throw new IllegalStateException("Investigation is already closed");
        }
        validateClose(outcome, penaltySeconds);
        
        Instant closedAt = Instant.now();
        raiseEvent(new InvestigationClosedEvent(
            getId(),
            outcome,
            penaltySeconds,
            closedAt
        ));
    }

    /**
     * Reopens a closed investigation (tribunal escalation).
     * 
     * @throws IllegalStateException if investigation is already open
     */
    public void reopen() {
        if (isOpen()) {
            throw new IllegalStateException("Investigation is already open");
        }
        
        raiseEvent(new InvestigationReopenedEvent(getId()));
    }

    /**
     * Returns true if the investigation is open (not closed).
     */
    public boolean isOpen() {
        return closedAt == null;
    }

    private static void validateOpen(UUID regattaId, UUID entryId, String description) {
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        if (entryId == null) {
            throw new IllegalArgumentException("Entry ID cannot be null");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be null or blank");
        }
    }

    private void validateClose(InvestigationOutcome outcome, Integer penaltySeconds) {
        if (outcome == null) {
            throw new IllegalArgumentException("Outcome cannot be null");
        }
        
        if (outcome == InvestigationOutcome.PENALTY) {
            if (penaltySeconds == null) {
                throw new IllegalArgumentException(
                    "Penalty seconds are required when outcome is PENALTY");
            }
            if (penaltySeconds <= 0) {
                throw new IllegalArgumentException(
                    "Penalty seconds must be positive when outcome is PENALTY");
            }
        }
    }

    @Override
    protected void applyEventToState(DomainEvent event) {
        if (event instanceof InvestigationOpenedEvent opened) {
            this.regattaId = opened.getRegattaId();
            this.entryId = opened.getEntryId();
            this.description = opened.getDescription();
        } else if (event instanceof InvestigationUpdatedEvent updated) {
            this.description = updated.getDescription();
        } else if (event instanceof InvestigationClosedEvent closed) {
            this.outcome = closed.getOutcome();
            this.penaltySeconds = closed.getPenaltySeconds();
            this.closedAt = closed.getClosedAt();
        } else if (event instanceof InvestigationReopenedEvent) {
            this.outcome = null;
            this.penaltySeconds = null;
            this.closedAt = null;
        }
    }

    @Override
    public String getAggregateType() {
        return "Investigation";
    }

    // Getters for state inspection
    
    public UUID getRegattaId() {
        return regattaId;
    }
    
    public UUID getEntryId() {
        return entryId;
    }
    
    public String getDescription() {
        return description;
    }
    
    public InvestigationOutcome getOutcome() {
        return outcome;
    }
    
    public Integer getPenaltySeconds() {
        return penaltySeconds;
    }
    
    public Instant getClosedAt() {
        return closedAt;
    }
}
