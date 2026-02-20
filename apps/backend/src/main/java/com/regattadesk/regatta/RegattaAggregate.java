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
    
    /**
     * Creates a new regatta aggregate.
     */
    public RegattaAggregate(UUID id) {
        super(id);
        this.status = "draft";
        this.drawRevision = 0;
        this.resultsRevision = 0;
    }
    
    /**
     * Creates a new regatta.
     */
    public static RegattaAggregate create(UUID id, String name, String description, 
                                         String timeZone, BigDecimal entryFee, String currency) {
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
        
        RegattaAggregate regatta = new RegattaAggregate(id);
        regatta.raiseEvent(new RegattaCreatedEvent(id, name, description, timeZone, entryFee, normalizedCurrency));
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
        raiseEvent(new EntryAddedEvent(getId(), entryId, eventId, blockId, crewId, billingClubId));
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
        } else if (event instanceof DrawPublishedEvent published) {
            this.drawRevision = published.getDrawRevision();
        } else if (event instanceof EntryAddedEvent) {
            // Entry tracking would be handled in a separate projection
            // This aggregate only needs to track if draw has been published
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
}
