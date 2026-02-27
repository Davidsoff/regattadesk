package com.regattadesk.regatta;

import com.regattadesk.aggregate.AggregateRoot;
import com.regattadesk.eventstore.DomainEvent;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

/**
 * Regatta aggregate root.
 * 
 * Represents a single regatta (head race event).
 * Manages regatta lifecycle, configuration, and revision tracking.
 */
public class RegattaAggregate extends AggregateRoot<RegattaAggregate> {
    
    private String name;
    private String description;
    private String timeZone;
    private String status;
    private BigDecimal entryFee;
    private String currency;
    private int drawRevision;
    private int resultsRevision;
    private Integer defaultPenaltySeconds;
    private Boolean allowCustomPenaltySeconds;
    
    /**
     * Creates a new regatta aggregate.
     */
    public RegattaAggregate(UUID id) {
        super(id);
        this.status = "draft";
        this.drawRevision = 0;
        this.resultsRevision = 0;
        this.defaultPenaltySeconds = 60; // Default 60 seconds
        this.allowCustomPenaltySeconds = false; // Default false
    }
    
    /**
     * Creates a new regatta.
     */
    public static RegattaAggregate create(UUID id, String name, String description, 
                                         String timeZone, BigDecimal entryFee, String currency,
                                         Integer defaultPenaltySeconds, Boolean allowCustomPenaltySeconds) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Regatta name cannot be null or blank");
        }
        if (timeZone == null || timeZone.isBlank()) {
            throw new IllegalArgumentException("Time zone cannot be null or blank");
        }
        try {
            ZoneId.of(timeZone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Time zone must be a valid IANA zone ID", e);
        }
        if (entryFee == null || entryFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Entry fee cannot be null or negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be null or blank");
        }
        String normalizedCurrency = currency.toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(normalizedCurrency);
        } catch (Exception e) {
            throw new IllegalArgumentException("Currency must be a valid ISO 4217 currency code", e);
        }
        
        // Validate penalty configuration
        if (defaultPenaltySeconds == null) {
            defaultPenaltySeconds = 60; // Default
        }
        if (defaultPenaltySeconds < 0) {
            throw new IllegalArgumentException("Default penalty seconds cannot be negative");
        }
        if (allowCustomPenaltySeconds == null) {
            allowCustomPenaltySeconds = false; // Default
        }
        
        RegattaAggregate regatta = new RegattaAggregate(id);
        regatta.raiseEvent(new RegattaCreatedEvent(id, name, description, timeZone, entryFee, 
                                                   normalizedCurrency, defaultPenaltySeconds, 
                                                   allowCustomPenaltySeconds));
        return regatta;
    }
    
    /**
     * Publishes the draw with the given seed.
     * Increments the draw revision.
     */
    public void publishDraw(long drawSeed) {
        int newDrawRevision = this.drawRevision + 1;
        raiseEvent(new DrawPublishedEvent(getId(), drawSeed, newDrawRevision));
    }
    
    /**
     * Adds an entry to the regatta.
     * In v0.1, this is prohibited after draw publication.
     */
    public void addEntry(UUID entryId, UUID eventId, UUID blockId, UUID crewId, UUID billingClubId) {
        if (drawRevision > 0) {
            throw new IllegalStateException("Cannot add entry after draw publication in v0.1");
        }
        if (entryId == null) throw new IllegalArgumentException("Entry ID cannot be null");
        if (eventId == null) throw new IllegalArgumentException("Event ID cannot be null");
        if (blockId == null) throw new IllegalArgumentException("Block ID cannot be null");
        if (crewId == null) throw new IllegalArgumentException("Crew ID cannot be null");
        raiseEvent(new EntryAddedEvent(getId(), entryId, eventId, blockId, crewId, billingClubId));
    }
    
    /**
     * Updates the penalty configuration for the regatta (BC07-001).
     * 
     * @param defaultPenaltySeconds default penalty seconds for investigations
     * @param allowCustomPenaltySeconds whether custom penalty seconds are allowed
     */
    public void updatePenaltyConfiguration(Integer defaultPenaltySeconds, Boolean allowCustomPenaltySeconds) {
        if (defaultPenaltySeconds == null) {
            throw new IllegalArgumentException("Default penalty seconds cannot be null");
        }
        if (defaultPenaltySeconds < 0) {
            throw new IllegalArgumentException("Default penalty seconds cannot be negative");
        }
        if (allowCustomPenaltySeconds == null) {
            throw new IllegalArgumentException("Allow custom penalty seconds cannot be null");
        }
        
        raiseEvent(new RegattaPenaltyConfigurationUpdatedEvent(
            getId(), defaultPenaltySeconds, allowCustomPenaltySeconds
        ));
    }
    
    /**
     * Increments the results revision (BC07-002).
     * 
     * Results revision changes affect public results pages and require cache invalidation.
     * Should be called when any result-affecting change occurs (DSQ, exclusion, penalties, etc.)
     * 
     * @param reason reason for incrementing the results revision
     * @throws IllegalArgumentException if reason is null or blank
     */
    public void incrementResultsRevision(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason cannot be null or blank");
        }
        
        int newRevision = this.resultsRevision + 1;
        raiseEvent(new ResultsRevisionIncrementedEvent(getId(), newRevision, reason));
    }
    
    @Override
    protected void applyEventToState(DomainEvent event) {
        if (event instanceof RegattaCreatedEvent created) {
            this.name = created.getName();
            this.description = created.getDescription();
            this.timeZone = created.getTimeZone();
            this.entryFee = created.getEntryFee();
            this.currency = created.getCurrency();
            this.status = "draft";
            this.defaultPenaltySeconds = created.getDefaultPenaltySeconds() != null
                ? created.getDefaultPenaltySeconds()
                : 60;
            this.allowCustomPenaltySeconds = created.getAllowCustomPenaltySeconds() != null
                ? created.getAllowCustomPenaltySeconds()
                : false;
        } else if (event instanceof DrawPublishedEvent published) {
            this.drawRevision = published.getDrawRevision();
        } else if (event instanceof EntryAddedEvent) {
            // Entry tracking would be handled in a separate projection
            // This aggregate only needs to track if draw has been published
        } else if (event instanceof RegattaPenaltyConfigurationUpdatedEvent penaltyUpdated) {
            this.defaultPenaltySeconds = penaltyUpdated.getDefaultPenaltySeconds();
            this.allowCustomPenaltySeconds = penaltyUpdated.getAllowCustomPenaltySeconds();
        } else if (event instanceof ResultsRevisionIncrementedEvent resultsIncremented) {
            this.resultsRevision = resultsIncremented.getNewResultsRevision();
        }
        // Additional event handlers will be added as more events are defined
    }
    
    @Override
    public String getAggregateType() {
        return "Regatta";
    }
    
    // Getters for state inspection (used in tests and projections)
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getTimeZone() {
        return timeZone;
    }
    
    public String getStatus() {
        return status;
    }
    
    public BigDecimal getEntryFee() {
        return entryFee;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public int getDrawRevision() {
        return drawRevision;
    }
    
    public int getResultsRevision() {
        return resultsRevision;
    }
    
    public Integer getDefaultPenaltySeconds() {
        return defaultPenaltySeconds;
    }
    
    public Boolean getAllowCustomPenaltySeconds() {
        return allowCustomPenaltySeconds;
    }
}
