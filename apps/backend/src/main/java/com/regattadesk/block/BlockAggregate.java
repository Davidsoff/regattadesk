package com.regattadesk.block;

import com.regattadesk.aggregate.AggregateRoot;
import com.regattadesk.eventstore.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Block aggregate root.
 * 
 * Represents a scheduling block in a regatta with timing configuration.
 * Manages block lifecycle and scheduling parameters.
 */
public class BlockAggregate extends AggregateRoot<BlockAggregate> {
    
    private UUID regattaId;
    private String name;
    private Instant startTime;
    private int eventIntervalSeconds;
    private int crewIntervalSeconds;
    private int displayOrder;
    private boolean deleted;
    
    /**
     * Creates a new block aggregate.
     */
    public BlockAggregate(UUID id) {
        super(id);
        this.deleted = false;
    }
    
    /**
     * Creates a new block.
     */
    public static BlockAggregate create(
            UUID blockId,
            UUID regattaId,
            String name,
            Instant startTime,
            int eventIntervalSeconds,
            int crewIntervalSeconds,
            int displayOrder
    ) {
        validateCreate(regattaId, name, startTime, eventIntervalSeconds, crewIntervalSeconds);
        
        var aggregate = new BlockAggregate(blockId);
        aggregate.raiseEvent(new BlockCreatedEvent(
                blockId,
                regattaId,
                name,
                startTime,
                eventIntervalSeconds,
                crewIntervalSeconds,
                displayOrder
        ));
        return aggregate;
    }
    
    /**
     * Updates the block configuration.
     */
    public void update(
            String name,
            Instant startTime,
            int eventIntervalSeconds,
            int crewIntervalSeconds,
            int displayOrder
    ) {
        if (deleted) {
            throw new IllegalStateException("Cannot update deleted block");
        }
        validateUpdate(name, startTime, eventIntervalSeconds, crewIntervalSeconds);
        
        raiseEvent(new BlockUpdatedEvent(
                getId(),
                name,
                startTime,
                eventIntervalSeconds,
                crewIntervalSeconds,
                displayOrder
        ));
    }
    
    /**
     * Deletes the block.
     */
    public void delete() {
        if (deleted) {
            throw new IllegalStateException("Block already deleted");
        }
        raiseEvent(new BlockDeletedEvent(getId()));
    }
    
    @Override
    protected void applyEventToState(DomainEvent event) {
        if (event instanceof BlockCreatedEvent created) {
            this.regattaId = created.getRegattaId();
            this.name = created.getName();
            this.startTime = created.getStartTime();
            this.eventIntervalSeconds = created.getEventIntervalSeconds();
            this.crewIntervalSeconds = created.getCrewIntervalSeconds();
            this.displayOrder = created.getDisplayOrder();
        } else if (event instanceof BlockUpdatedEvent updated) {
            this.name = updated.getName();
            this.startTime = updated.getStartTime();
            this.eventIntervalSeconds = updated.getEventIntervalSeconds();
            this.crewIntervalSeconds = updated.getCrewIntervalSeconds();
            this.displayOrder = updated.getDisplayOrder();
        } else if (event instanceof BlockDeletedEvent) {
            this.deleted = true;
        }
    }
    
    @Override
    public String getAggregateType() {
        return "Block";
    }
    
    private static void validateCreate(
            UUID regattaId,
            String name,
            Instant startTime,
            int eventIntervalSeconds,
            int crewIntervalSeconds
    ) {
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Block name cannot be null or blank");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Start time cannot be null");
        }
        if (eventIntervalSeconds < 0) {
            throw new IllegalArgumentException("Event interval seconds cannot be negative");
        }
        if (crewIntervalSeconds < 0) {
            throw new IllegalArgumentException("Crew interval seconds cannot be negative");
        }
    }
    
    private static void validateUpdate(
            String name,
            Instant startTime,
            int eventIntervalSeconds,
            int crewIntervalSeconds
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Block name cannot be null or blank");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Start time cannot be null");
        }
        if (eventIntervalSeconds < 0) {
            throw new IllegalArgumentException("Event interval seconds cannot be negative");
        }
        if (crewIntervalSeconds < 0) {
            throw new IllegalArgumentException("Crew interval seconds cannot be negative");
        }
    }
    
    // Getters for state inspection
    
    public UUID getRegattaId() {
        return regattaId;
    }
    
    public String getName() {
        return name;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public int getEventIntervalSeconds() {
        return eventIntervalSeconds;
    }
    
    public int getCrewIntervalSeconds() {
        return crewIntervalSeconds;
    }
    
    public int getDisplayOrder() {
        return displayOrder;
    }
    
    public boolean isDeleted() {
        return deleted;
    }
}
