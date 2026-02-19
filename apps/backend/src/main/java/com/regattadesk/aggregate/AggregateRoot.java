package com.regattadesk.aggregate;

import com.regattadesk.eventstore.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Base class for all aggregate roots in the system.
 * 
 * Aggregates are the consistency boundary for domain operations.
 * They load their state by replaying events and emit new events for state changes.
 * 
 * @param <T> The concrete aggregate type
 */
public abstract class AggregateRoot<T extends AggregateRoot<T>> {
    
    private final UUID id;
    private long version;
    private final List<DomainEvent> uncommittedEvents;
    
    /**
     * Creates a new aggregate with the given ID.
     * 
     * @param id the aggregate ID
     */
    protected AggregateRoot(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Aggregate ID cannot be null");
        }
        this.id = id;
        this.version = -1;
        this.uncommittedEvents = new ArrayList<>();
    }
    
    /**
     * Returns the aggregate ID.
     */
    public UUID getId() {
        return id;
    }
    
    /**
     * Returns the current version of the aggregate.
     * Version -1 indicates a new aggregate that hasn't been persisted yet.
     */
    public long getVersion() {
        return version;
    }
    
    /**
     * Returns the uncommitted events that need to be persisted.
     * These events represent state changes since the aggregate was loaded.
     */
    public List<DomainEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }
    
    /**
     * Marks all uncommitted events as committed.
     * Should be called after events have been successfully persisted.
     */
    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }
    
    /**
     * Loads the aggregate state from a stream of events.
     * 
     * @param events the events to replay
     * @return the aggregate instance with replayed state
     */
    @SuppressWarnings("unchecked")
    public T loadFromHistory(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            applyEvent(event, false);
        }
        return (T) this;
    }
    
    /**
     * Applies an event to the aggregate.
     * When isNew is true, the event is added to uncommitted events.
     * When isNew is false, the event is replayed from history.
     * 
     * @param event the event to apply
     * @param isNew whether this is a new event or a historical event
     */
    protected void applyEvent(DomainEvent event, boolean isNew) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        
        // Apply the event to update state
        applyEventToState(event);

        // Version tracks the last applied event sequence using event-store semantics:
        // new aggregate starts at -1, first event moves to 0, etc.
        this.version++;
        
        // If this is a new event, add it to uncommitted events
        if (isNew) {
            uncommittedEvents.add(event);
        }
    }
    
    /**
     * Applies a new event to the aggregate and records it as uncommitted.
     * 
     * @param event the new event to apply
     */
    protected void raiseEvent(DomainEvent event) {
        applyEvent(event, true);
    }
    
    /**
     * Applies the event to the aggregate's internal state.
     * Concrete aggregates must implement this to update their state based on events.
     * 
     * @param event the event to apply
     */
    protected abstract void applyEventToState(DomainEvent event);
    
    /**
     * Returns the aggregate type identifier.
     * Used for persistence and event store categorization.
     */
    public abstract String getAggregateType();
}
