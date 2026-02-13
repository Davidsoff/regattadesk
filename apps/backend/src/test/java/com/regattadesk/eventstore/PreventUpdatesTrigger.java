package com.regattadesk.eventstore;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * H2 trigger to prevent updates to event_store table (append-only constraint).
 */
public class PreventUpdatesTrigger implements Trigger {
    
    @Override
    public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type) {
        // Initialization if needed
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("event_store is append-only: updates are not allowed");
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
