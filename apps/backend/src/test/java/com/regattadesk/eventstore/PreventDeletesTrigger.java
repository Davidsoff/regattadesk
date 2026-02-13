package com.regattadesk.eventstore;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * H2 trigger to prevent deletes from event_store table (immutability constraint).
 */
public class PreventDeletesTrigger implements Trigger {
    
    @Override
    public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type) {
        // Initialization if needed
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("event_store is immutable: deletes are not allowed");
    }

    @Override
    public void close() {
        // Cleanup if needed
    }

    @Override
    public void remove() {
        // Remove if needed
    }
}
