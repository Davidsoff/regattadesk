package com.regattadesk.bibpool;

import com.regattadesk.aggregate.AggregateRoot;
import com.regattadesk.eventstore.DomainEvent;

import java.util.List;
import java.util.UUID;

/**
 * BibPool aggregate root.
 * 
 * Represents a pool of bib numbers that can be allocated to entries.
 * Supports both range-based and explicit list allocation modes.
 */
public class BibPoolAggregate extends AggregateRoot<BibPoolAggregate> {
    
    private UUID regattaId;
    private UUID blockId;
    private String name;
    private String allocationMode;
    private Integer startBib;
    private Integer endBib;
    private List<Integer> bibNumbers;
    private int priority;
    private boolean isOverflow;
    private boolean deleted;
    
    /**
     * Creates a new bib pool aggregate.
     */
    public BibPoolAggregate(UUID id) {
        super(id);
        this.deleted = false;
    }
    
    /**
     * Creates a new bib pool with range allocation.
     */
    public static BibPoolAggregate createWithRange(
            UUID poolId,
            UUID regattaId,
            UUID blockId,
            String name,
            int startBib,
            int endBib,
            int priority,
            boolean isOverflow
    ) {
        validateCreate(regattaId, name, priority);
        validateRange(startBib, endBib);
        
        var aggregate = new BibPoolAggregate(poolId);
        aggregate.raiseEvent(new BibPoolCreatedEvent(
                poolId,
                regattaId,
                blockId,
                name,
                "range",
                startBib,
                endBib,
                null,
                priority,
                isOverflow
        ));
        return aggregate;
    }
    
    /**
     * Creates a new bib pool with explicit list allocation.
     */
    public static BibPoolAggregate createWithExplicitList(
            UUID poolId,
            UUID regattaId,
            UUID blockId,
            String name,
            List<Integer> bibNumbers,
            int priority,
            boolean isOverflow
    ) {
        validateCreate(regattaId, name, priority);
        validateExplicitList(bibNumbers);
        
        var aggregate = new BibPoolAggregate(poolId);
        aggregate.raiseEvent(new BibPoolCreatedEvent(
                poolId,
                regattaId,
                blockId,
                name,
                "explicit_list",
                null,
                null,
                bibNumbers,
                priority,
                isOverflow
        ));
        return aggregate;
    }
    
    /**
     * Updates the bib pool with range allocation.
     */
    public void updateRange(
            String name,
            int startBib,
            int endBib,
            int priority
    ) {
        if (deleted) {
            throw new IllegalStateException("Cannot update deleted bib pool");
        }
        validateUpdate(name, priority);
        validateRange(startBib, endBib);
        
        raiseEvent(new BibPoolUpdatedEvent(
                getId(),
                name,
                "range",
                startBib,
                endBib,
                null,
                priority
        ));
    }
    
    /**
     * Updates the bib pool with explicit list allocation.
     */
    public void updateExplicitList(
            String name,
            List<Integer> bibNumbers,
            int priority
    ) {
        if (deleted) {
            throw new IllegalStateException("Cannot update deleted bib pool");
        }
        validateUpdate(name, priority);
        validateExplicitList(bibNumbers);
        
        raiseEvent(new BibPoolUpdatedEvent(
                getId(),
                name,
                "explicit_list",
                null,
                null,
                bibNumbers,
                priority
        ));
    }
    
    /**
     * Deletes the bib pool.
     */
    public void delete() {
        if (deleted) {
            throw new IllegalStateException("Bib pool already deleted");
        }
        raiseEvent(new BibPoolDeletedEvent(getId()));
    }
    
    @Override
    protected void applyEventToState(DomainEvent event) {
        if (event instanceof BibPoolCreatedEvent created) {
            this.regattaId = created.getRegattaId();
            this.blockId = created.getBlockId();
            this.name = created.getName();
            this.allocationMode = created.getAllocationMode();
            this.startBib = created.getStartBib();
            this.endBib = created.getEndBib();
            this.bibNumbers = created.getBibNumbers();
            this.priority = created.getPriority();
            this.isOverflow = created.isOverflow();
        } else if (event instanceof BibPoolUpdatedEvent updated) {
            this.name = updated.getName();
            this.allocationMode = updated.getAllocationMode();
            this.startBib = updated.getStartBib();
            this.endBib = updated.getEndBib();
            this.bibNumbers = updated.getBibNumbers();
            this.priority = updated.getPriority();
        } else if (event instanceof BibPoolDeletedEvent) {
            this.deleted = true;
        }
    }
    
    @Override
    public String getAggregateType() {
        return "BibPool";
    }
    
    private static void validateCreate(UUID regattaId, String name, int priority) {
        if (regattaId == null) {
            throw new IllegalArgumentException("Regatta ID cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bib pool name cannot be null or blank");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("Priority cannot be negative");
        }
    }
    
    private static void validateUpdate(String name, int priority) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bib pool name cannot be null or blank");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("Priority cannot be negative");
        }
    }
    
    private static void validateRange(int startBib, int endBib) {
        if (startBib > endBib) {
            throw new IllegalArgumentException("Start bib cannot be greater than end bib");
        }
    }
    
    private static void validateExplicitList(List<Integer> bibNumbers) {
        if (bibNumbers == null || bibNumbers.isEmpty()) {
            throw new IllegalArgumentException("Bib numbers list cannot be null or empty");
        }
    }
    
    // Getters for state inspection
    
    public UUID getRegattaId() {
        return regattaId;
    }
    
    public UUID getBlockId() {
        return blockId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getAllocationMode() {
        return allocationMode;
    }
    
    public Integer getStartBib() {
        return startBib;
    }
    
    public Integer getEndBib() {
        return endBib;
    }
    
    public List<Integer> getBibNumbers() {
        return bibNumbers;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public boolean isOverflow() {
        return isOverflow;
    }
    
    public boolean isDeleted() {
        return deleted;
    }
}
