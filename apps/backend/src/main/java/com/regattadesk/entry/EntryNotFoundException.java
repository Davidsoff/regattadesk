package com.regattadesk.entry;

import java.util.UUID;

/**
 * Thrown when an entry cannot be found.
 */
public class EntryNotFoundException extends RuntimeException {

    public EntryNotFoundException(UUID entryId) {
        super("Entry not found: " + entryId);
    }
}
