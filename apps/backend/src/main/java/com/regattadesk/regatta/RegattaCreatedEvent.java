package com.regattadesk.regatta;

import com.regattadesk.eventstore.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event emitted when a new regatta is created.
 */
public class RegattaCreatedEvent implements DomainEvent {
    
    private final UUID regattaId;
    private final String name;
    private final String description;
    private final String timeZone;
    private final BigDecimal entryFee;
    private final String currency;
    
    public RegattaCreatedEvent(UUID regattaId, String name, String description, 
                              String timeZone, BigDecimal entryFee, String currency) {
        this.regattaId = regattaId;
        this.name = name;
        this.description = description;
        this.timeZone = timeZone;
        this.entryFee = entryFee;
        this.currency = currency;
    }
    
    @Override
    public String getEventType() {
        return "RegattaCreated";
    }
    
    @Override
    public UUID getAggregateId() {
        return regattaId;
    }
    
    public UUID getRegattaId() {
        return regattaId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getTimeZone() {
        return timeZone;
    }
    
    public BigDecimal getEntryFee() {
        return entryFee;
    }
    
    public String getCurrency() {
        return currency;
    }
}
