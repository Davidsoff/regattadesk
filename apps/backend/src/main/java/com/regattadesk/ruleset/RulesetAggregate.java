package com.regattadesk.ruleset;

import com.regattadesk.aggregate.AggregateRoot;
import com.regattadesk.eventstore.DomainEvent;

import java.util.UUID;

/**
 * Ruleset aggregate root.
 * 
 * Represents a ruleset that controls entry validity and scheduling rules.
 * Rulesets can be versioned, duplicated, and updated before draw publication.
 */
public class RulesetAggregate extends AggregateRoot<RulesetAggregate> {
    
    private static final String AGE_CALCULATION_ACTUAL_AT_START = "actual_at_start";
    private static final String AGE_CALCULATION_AGE_AS_OF_JAN_1 = "age_as_of_jan_1";
    
    private String name;
    private String rulesetVersion;
    private String description;
    private String ageCalculationType;
    private boolean isGlobal;
    
    /**
     * Creates a new ruleset aggregate.
     */
    public RulesetAggregate(UUID id) {
        super(id);
        this.isGlobal = false;
    }
    
    /**
     * Creates a new ruleset.
     */
    public static RulesetAggregate create(UUID id, String name, String rulesetVersion, 
                                         String description, String ageCalculationType) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ruleset name cannot be null or blank");
        }
        if (rulesetVersion == null || rulesetVersion.isBlank()) {
            throw new IllegalArgumentException("Version cannot be null or blank");
        }
        if (ageCalculationType == null || ageCalculationType.isBlank()) {
            throw new IllegalArgumentException("Age calculation type cannot be null or blank");
        }
        
        String normalizedAgeCalcType = ageCalculationType.toLowerCase();
        if (!normalizedAgeCalcType.equals(AGE_CALCULATION_ACTUAL_AT_START) && 
            !normalizedAgeCalcType.equals(AGE_CALCULATION_AGE_AS_OF_JAN_1)) {
            throw new IllegalArgumentException(
                "Age calculation type must be 'actual_at_start' or 'age_as_of_jan_1'"
            );
        }
        
        RulesetAggregate ruleset = new RulesetAggregate(id);
        ruleset.raiseEvent(new RulesetCreatedEvent(
            id, name, rulesetVersion, description, normalizedAgeCalcType, false
        ));
        return ruleset;
    }
    
    /**
     * Duplicates an existing ruleset with a new ID and version.
     */
    public static RulesetAggregate duplicate(UUID newId, RulesetAggregate source, 
                                            String newName, String newRulesetVersion) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Ruleset name cannot be null or blank");
        }
        if (newRulesetVersion == null || newRulesetVersion.isBlank()) {
            throw new IllegalArgumentException("Version cannot be null or blank");
        }
        
        RulesetAggregate ruleset = new RulesetAggregate(newId);
        ruleset.raiseEvent(new RulesetDuplicatedEvent(
            newId, 
            source.getId(),
            newName,
            newRulesetVersion,
            source.description,
            source.ageCalculationType,
            false  // Duplicated rulesets start as non-global
        ));
        return ruleset;
    }
    
    /**
     * Updates the ruleset properties.
     */
    public void update(String name, String rulesetVersion, String description, String ageCalculationType) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ruleset name cannot be null or blank");
        }
        if (rulesetVersion == null || rulesetVersion.isBlank()) {
            throw new IllegalArgumentException("Version cannot be null or blank");
        }
        if (ageCalculationType == null || ageCalculationType.isBlank()) {
            throw new IllegalArgumentException("Age calculation type cannot be null or blank");
        }
        
        String normalizedAgeCalcType = ageCalculationType.toLowerCase();
        if (!normalizedAgeCalcType.equals(AGE_CALCULATION_ACTUAL_AT_START) && 
            !normalizedAgeCalcType.equals(AGE_CALCULATION_AGE_AS_OF_JAN_1)) {
            throw new IllegalArgumentException(
                "Age calculation type must be 'actual_at_start' or 'age_as_of_jan_1'"
            );
        }
        
        raiseEvent(new RulesetUpdatedEvent(
            getId(), name, rulesetVersion, description, normalizedAgeCalcType
        ));
    }
    
    @Override
    protected void applyEventToState(DomainEvent event) {
        if (event instanceof RulesetCreatedEvent created) {
            this.name = created.getName();
            this.rulesetVersion = created.getVersion();
            this.description = created.getDescription();
            this.ageCalculationType = created.getAgeCalculationType();
            this.isGlobal = created.isGlobal();
        } else if (event instanceof RulesetDuplicatedEvent duplicated) {
            this.name = duplicated.getName();
            this.rulesetVersion = duplicated.getVersion();
            this.description = duplicated.getDescription();
            this.ageCalculationType = duplicated.getAgeCalculationType();
            this.isGlobal = duplicated.isGlobal();
        } else if (event instanceof RulesetUpdatedEvent updated) {
            this.name = updated.getName();
            this.rulesetVersion = updated.getVersion();
            this.description = updated.getDescription();
            this.ageCalculationType = updated.getAgeCalculationType();
        }
    }
    
    @Override
    public String getAggregateType() {
        return "Ruleset";
    }
    
    // Getters for state inspection (used in tests and projections)
    
    public String getName() {
        return name;
    }
    
    public String getRulesetVersion() {
        return rulesetVersion;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getAgeCalculationType() {
        return ageCalculationType;
    }
    
    public boolean isGlobal() {
        return isGlobal;
    }
}
