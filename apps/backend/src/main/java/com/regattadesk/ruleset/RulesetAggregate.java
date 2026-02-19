package com.regattadesk.ruleset;

import com.regattadesk.aggregate.AggregateRoot;
import com.regattadesk.eventstore.DomainEvent;
import org.jboss.logging.Logger;

import java.util.Locale;
import java.util.UUID;

/**
 * Ruleset aggregate root.
 * 
 * Represents a ruleset that controls entry validity and scheduling rules.
 * Rulesets can be versioned, duplicated, and updated before draw publication.
 */
public class RulesetAggregate extends AggregateRoot<RulesetAggregate> {

    private static final Logger LOG = Logger.getLogger(RulesetAggregate.class);
    
    private static final String AGE_CALCULATION_ACTUAL_AT_START = "actual_at_start";
    private static final String AGE_CALCULATION_AGE_AS_OF_JAN_1 = "age_as_of_jan_1";
    
    private String name;
    private String rulesetVersion;
    private String description;
    private String ageCalculationType;
    private boolean isGlobal;
    private boolean drawPublished;
    
    /**
     * Creates a new ruleset aggregate.
     */
    public RulesetAggregate(UUID id) {
        super(id);
        this.isGlobal = false;
        this.drawPublished = false;
    }
    
    /**
     * Creates a new ruleset.
     */
    public static RulesetAggregate create(UUID id, String name, String rulesetVersion, 
                                         String description, String ageCalculationType) {
        validateName(name);
        validateVersion(rulesetVersion);
        String normalizedAgeCalcType = validateAndNormalizeAgeCalculationType(ageCalculationType);
        
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
        if (source == null) {
            throw new IllegalArgumentException("source cannot be null");
        }
        validateName(newName);
        validateVersion(newRulesetVersion);
        if (source.drawPublished) {
            throw new IllegalStateException("Cannot duplicate ruleset after draw publication");
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
        if (drawPublished) {
            throw new IllegalStateException("Cannot update ruleset after draw publication");
        }
        validateName(name);
        validateVersion(rulesetVersion);
        String normalizedAgeCalcType = validateAndNormalizeAgeCalculationType(ageCalculationType);
        
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
        } else if (event instanceof RulesetDrawPublishedEvent) {
            this.drawPublished = true;
        } else {
            LOG.warnf(
                "Ignoring unknown event type during ruleset replay: eventType=%s aggregateId=%s",
                event.getClass().getName(),
                getId()
            );
        }
    }

    public void markDrawPublished() {
        if (!drawPublished) {
            raiseEvent(new RulesetDrawPublishedEvent(getId()));
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

    public boolean isDrawPublished() {
        return drawPublished;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ruleset name cannot be null or blank");
        }
    }

    private static void validateVersion(String rulesetVersion) {
        if (rulesetVersion == null || rulesetVersion.isBlank()) {
            throw new IllegalArgumentException("Version cannot be null or blank");
        }
    }

    private static String validateAndNormalizeAgeCalculationType(String ageCalculationType) {
        if (ageCalculationType == null || ageCalculationType.isBlank()) {
            throw new IllegalArgumentException("Age calculation type cannot be null or blank");
        }
        String normalized = ageCalculationType.toLowerCase(Locale.ROOT);
        if (!normalized.equals(AGE_CALCULATION_ACTUAL_AT_START)
            && !normalized.equals(AGE_CALCULATION_AGE_AS_OF_JAN_1)) {
            throw new IllegalArgumentException(
                "Age calculation type must be 'actual_at_start' or 'age_as_of_jan_1'"
            );
        }
        return normalized;
    }
}
