package com.regattadesk.regatta;

import com.regattadesk.aggregate.AggregateRoot;
import com.regattadesk.eventstore.DomainEvent;

import java.math.BigDecimal;
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
        if (entryFee == null || entryFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Entry fee cannot be null or negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be null or blank");
        }
        
        RegattaAggregate regatta = new RegattaAggregate(id);
        regatta.raiseEvent(new RegattaCreatedEvent(id, name, description, timeZone, entryFee, currency));
        return regatta;
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
