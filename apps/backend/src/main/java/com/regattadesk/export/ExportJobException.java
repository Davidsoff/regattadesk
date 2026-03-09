package com.regattadesk.export;

/**
 * Unchecked exception wrapping persistence and generation failures in the export subsystem.
 */
public class ExportJobException extends RuntimeException {

    public ExportJobException(String message) {
        super(message);
    }

    public ExportJobException(String message, Throwable cause) {
        super(message, cause);
    }
}
