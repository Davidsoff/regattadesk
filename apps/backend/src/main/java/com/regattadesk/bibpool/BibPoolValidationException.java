package com.regattadesk.bibpool;

import java.util.List;
import java.util.UUID;

public class BibPoolValidationException extends RuntimeException {
    private final List<Integer> overlappingBibs;
    private final UUID conflictingPoolId;
    private final String conflictingPoolName;

    public BibPoolValidationException(
        String message,
        List<Integer> overlappingBibs,
        UUID conflictingPoolId,
        String conflictingPoolName
    ) {
        super(message);
        this.overlappingBibs = overlappingBibs;
        this.conflictingPoolId = conflictingPoolId;
        this.conflictingPoolName = conflictingPoolName;
    }

    public List<Integer> getOverlappingBibs() {
        return overlappingBibs;
    }

    public UUID getConflictingPoolId() {
        return conflictingPoolId;
    }

    public String getConflictingPoolName() {
        return conflictingPoolName;
    }
}
