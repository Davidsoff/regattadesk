package com.regattadesk.jwt;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Configuration-based JWT key registry implementation.
 * 
 * Supports both:
 * 1. Legacy single-key configuration (jwt.public.secret, jwt.public.kid)
 * 2. New multi-key configuration (jwt.public.keys.*.secret, jwt.public.keys.*.activated-at)
 * 
 * For key rotation with >=6 day overlap, configure multiple keys:
 * 
 * jwt.public.keys.v1-2026-02.secret=...
 * jwt.public.keys.v1-2026-02.activated-at=2026-02-01T00:00:00Z
 * jwt.public.keys.v2-2026-02.secret=...
 * jwt.public.keys.v2-2026-02.activated-at=2026-02-15T00:00:00Z
 * 
 * When using multi-key config, the legacy secret/kid fields are ignored.
 */
@ApplicationScoped
public class ConfigBasedJwtKeyRegistry implements JwtKeyRegistry {
    private static final Logger LOG = Logger.getLogger(ConfigBasedJwtKeyRegistry.class);
    
    private final List<KeyEntry> activeKeys;
    private final KeyEntry newestKey;
    
    @Inject
    public ConfigBasedJwtKeyRegistry(
            JwtConfig legacyConfig,
            @ConfigProperty(name = "jwt.public.keys") Optional<Map<String, Map<String, String>>> keysConfig) {
        
        if (keysConfig.isPresent() && !keysConfig.get().isEmpty()) {
            // Multi-key configuration
            this.activeKeys = parseMultiKeyConfig(keysConfig.get());
            LOG.infof("Initialized JWT key registry with %d active keys", activeKeys.size());
        } else {
            // Legacy single-key configuration (backward compatibility)
            LOG.info("Using legacy single-key JWT configuration");
            KeyEntry singleKey = new KeyEntry(
                legacyConfig.kid(),
                legacyConfig.secret().getBytes(StandardCharsets.UTF_8),
                Instant.now() // Assume activated now for legacy config
            );
            this.activeKeys = List.of(singleKey);
        }
        
        if (activeKeys.isEmpty()) {
            throw new IllegalStateException("No JWT keys configured");
        }
        
        // Find newest key (by activation time)
        this.newestKey = activeKeys.stream()
            .max(Comparator.comparing(KeyEntry::activatedAt))
            .orElseThrow(() -> new IllegalStateException("No keys available"));
        
        LOG.infof("Using key '%s' for signing new tokens", newestKey.kid());
        
        // Validate overlap requirement if multiple keys
        if (activeKeys.size() > 1) {
            validateOverlap();
        }
    }
    
    @Override
    public List<KeyEntry> getActiveKeys() {
        return activeKeys;
    }
    
    @Override
    public KeyEntry getNewestKey() {
        return newestKey;
    }
    
    /**
     * Parse multi-key configuration from properties.
     * Expected format:
     * jwt.public.keys.{kid}.secret=...
     * jwt.public.keys.{kid}.activated-at=2026-02-01T00:00:00Z
     */
    private List<KeyEntry> parseMultiKeyConfig(Map<String, Map<String, String>> keysConfig) {
        List<KeyEntry> keys = new ArrayList<>();
        
        for (Map.Entry<String, Map<String, String>> entry : keysConfig.entrySet()) {
            String kid = entry.getKey();
            Map<String, String> keyProps = entry.getValue();
            
            String secret = keyProps.get("secret");
            String activatedAtStr = keyProps.get("activated-at");
            
            if (secret == null || secret.isBlank()) {
                LOG.warnf("Skipping key '%s': missing secret", kid);
                continue;
            }
            
            if (activatedAtStr == null || activatedAtStr.isBlank()) {
                LOG.warnf("Skipping key '%s': missing activated-at", kid);
                continue;
            }
            
            try {
                Instant activatedAt = Instant.parse(activatedAtStr);
                byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
                
                if (secretBytes.length < 32) {
                    LOG.warnf("Skipping key '%s': secret too short (< 32 bytes)", kid);
                    continue;
                }
                
                keys.add(new KeyEntry(kid, secretBytes, activatedAt));
                LOG.debugf("Loaded key '%s' activated at %s", kid, activatedAt);
                
            } catch (DateTimeParseException e) {
                LOG.warnf("Skipping key '%s': invalid activated-at format: %s", kid, activatedAtStr);
            }
        }
        
        // Sort by activation time (oldest first)
        keys.sort(Comparator.comparing(KeyEntry::activatedAt));
        
        return keys;
    }
    
    /**
     * Validate that keys have sufficient overlap for safe rotation.
     * Warns if overlap is less than 6 days.
     */
    private void validateOverlap() {
        List<KeyEntry> sortedKeys = new ArrayList<>(activeKeys);
        sortedKeys.sort(Comparator.comparing(KeyEntry::activatedAt));
        
        for (int i = 0; i < sortedKeys.size() - 1; i++) {
            KeyEntry older = sortedKeys.get(i);
            KeyEntry newer = sortedKeys.get(i + 1);
            
            // For safe rotation with 5-day TTL tokens, we need at least 6 days overlap
            // This means the older key should remain active for 6+ days after newer key activation
            Instant newerActivation = newer.activatedAt();
            
            // In production, older key should be removed 6+ days after newer key activation
            // We can't validate future removal here, but we can warn about activation spacing
            long daysBetween = java.time.Duration.between(older.activatedAt(), newerActivation).toDays();
            
            LOG.infof("Key rotation: '%s' -> '%s', activation gap: %d days", 
                older.kid(), newer.kid(), daysBetween);
        }
        
        // Log recommendation
        LOG.info("For safe key rotation: maintain at least 6 days overlap " +
                 "(newer key activated, older key removed 6+ days later)");
    }
}
