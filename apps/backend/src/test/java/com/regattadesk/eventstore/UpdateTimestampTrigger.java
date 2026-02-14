package com.regattadesk.eventstore;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * H2 trigger to automatically update the updated_at column.
 */
public class UpdateTimestampTrigger implements Trigger {
    
    @Override
    public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type) {
        // Initialization if needed
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        if (newRow != null && newRow.length > 0) {
            // updated_at is the last column for both aggregates and operator_tokens tables.
            newRow[newRow.length - 1] = new Timestamp(System.currentTimeMillis());
        }
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
