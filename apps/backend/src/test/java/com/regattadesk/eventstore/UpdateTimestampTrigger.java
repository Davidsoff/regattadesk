package com.regattadesk.eventstore;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * H2 trigger to automatically update the updated_at column on aggregates table.
 */
public class UpdateTimestampTrigger implements Trigger {
    
    @Override
    public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type) {
        // Initialization if needed
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        if (newRow != null && newRow.length > 4) {
            // Set updated_at (index 4: id=0, aggregate_type=1, version=2, created_at=3, updated_at=4)
            newRow[4] = new Timestamp(System.currentTimeMillis());
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
