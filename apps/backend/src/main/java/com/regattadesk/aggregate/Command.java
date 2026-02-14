package com.regattadesk.aggregate;

import java.util.UUID;

/**
 * Base interface for all commands in the system.
 * 
 * Commands represent intentions to change state.
 * They are validated and then applied to aggregates, which emit events.
 */
public interface Command {
    /**
     * Returns the aggregate ID this command targets.
     * 
     * @return the aggregate ID
     */
    UUID getAggregateId();
}
