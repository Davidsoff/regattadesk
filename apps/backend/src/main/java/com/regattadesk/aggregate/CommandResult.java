package com.regattadesk.aggregate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Result of executing a command on an aggregate.
 * 
 * @param <T> The type of data returned by the command
 */
public class CommandResult<T> {
    
    private final boolean success;
    private final T data;
    private final List<String> errors;
    private final UUID aggregateId;
    
    private CommandResult(boolean success, T data, List<String> errors, UUID aggregateId) {
        this.success = success;
        this.data = data;
        this.errors = errors != null ? List.copyOf(errors) : Collections.emptyList();
        this.aggregateId = aggregateId;
    }
    
    /**
     * Creates a successful command result with data.
     */
    public static <T> CommandResult<T> success(UUID aggregateId, T data) {
        return new CommandResult<>(true, data, null, aggregateId);
    }
    
    /**
     * Creates a successful command result without data.
     */
    public static <T> CommandResult<T> success(UUID aggregateId) {
        return new CommandResult<>(true, null, null, aggregateId);
    }
    
    /**
     * Creates a failed command result with error messages.
     */
    public static <T> CommandResult<T> failure(UUID aggregateId, List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("Failure result must have at least one error");
        }
        return new CommandResult<>(false, null, errors, aggregateId);
    }
    
    /**
     * Creates a failed command result with a single error message.
     */
    public static <T> CommandResult<T> failure(UUID aggregateId, String error) {
        return failure(aggregateId, List.of(error));
    }
    
    /**
     * Returns whether the command was successful.
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Returns whether the command failed.
     */
    public boolean isFailure() {
        return !success;
    }
    
    /**
     * Returns the result data if successful.
     */
    public Optional<T> getData() {
        return Optional.ofNullable(data);
    }
    
    /**
     * Returns the list of error messages if failed.
     */
    public List<String> getErrors() {
        return errors;
    }
    
    /**
     * Returns the aggregate ID this result is for.
     */
    public UUID getAggregateId() {
        return aggregateId;
    }
}
