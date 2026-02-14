package com.regattadesk.aggregate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CommandResult.
 */
class CommandResultTest {
    
    @Test
    void testSuccessWithData() {
        UUID aggregateId = UUID.randomUUID();
        String data = "test data";
        
        CommandResult<String> result = CommandResult.success(aggregateId, data);
        
        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertTrue(result.getData().isPresent());
        assertEquals(data, result.getData().get());
        assertTrue(result.getErrors().isEmpty());
        assertEquals(aggregateId, result.getAggregateId());
    }
    
    @Test
    void testSuccessWithoutData() {
        UUID aggregateId = UUID.randomUUID();
        
        CommandResult<String> result = CommandResult.success(aggregateId);
        
        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertFalse(result.getData().isPresent());
        assertTrue(result.getErrors().isEmpty());
        assertEquals(aggregateId, result.getAggregateId());
    }
    
    @Test
    void testFailureWithSingleError() {
        UUID aggregateId = UUID.randomUUID();
        String error = "Something went wrong";
        
        CommandResult<String> result = CommandResult.failure(aggregateId, error);
        
        assertFalse(result.isSuccess());
        assertTrue(result.isFailure());
        assertFalse(result.getData().isPresent());
        assertEquals(1, result.getErrors().size());
        assertEquals(error, result.getErrors().get(0));
        assertEquals(aggregateId, result.getAggregateId());
    }
    
    @Test
    void testFailureWithMultipleErrors() {
        UUID aggregateId = UUID.randomUUID();
        List<String> errors = List.of("Error 1", "Error 2", "Error 3");
        
        CommandResult<String> result = CommandResult.failure(aggregateId, errors);
        
        assertFalse(result.isSuccess());
        assertTrue(result.isFailure());
        assertFalse(result.getData().isPresent());
        assertEquals(3, result.getErrors().size());
        assertEquals(errors, result.getErrors());
        assertEquals(aggregateId, result.getAggregateId());
    }
    
    @Test
    void testFailureWithNullErrorsThrowsException() {
        UUID aggregateId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            CommandResult.failure(aggregateId, (List<String>) null);
        });
    }
    
    @Test
    void testFailureWithEmptyErrorsThrowsException() {
        UUID aggregateId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            CommandResult.failure(aggregateId, List.of());
        });
    }
    
    @Test
    void testErrorsAreImmutable() {
        UUID aggregateId = UUID.randomUUID();
        List<String> errors = List.of("Error 1");
        
        CommandResult<String> result = CommandResult.failure(aggregateId, errors);
        
        assertThrows(UnsupportedOperationException.class, () -> {
            result.getErrors().add("Error 2");
        });
    }
}
