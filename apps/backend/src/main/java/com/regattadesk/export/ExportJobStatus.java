package com.regattadesk.export;

import java.util.Locale;

/**
 * Job status states for export jobs.
 *
 * <ul>
 *   <li>{@code PENDING} – job created, waiting to be picked up by worker</li>
 *   <li>{@code PROCESSING} – PDF is being generated</li>
 *   <li>{@code COMPLETED} – PDF artifact ready for download</li>
 *   <li>{@code FAILED} – generation failed; see error_message</li>
 * </ul>
 */
public enum ExportJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    /**
     * Returns the lowercase string representation used in the database and API.
     */
    public String value() {
        return name().toLowerCase();
    }

    /**
     * Parse from a lowercase database/API string.
     *
     * @param value lowercase status string
     * @return matching enum constant
     * @throws IllegalArgumentException if the value is not recognised
     */
    public static ExportJobStatus fromValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
