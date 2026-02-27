package com.regattadesk.jwt;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigBasedJwtKeyRegistryTest {

    @Test
    void getNewestKey_ignoresFutureActivatedKeys() {
        Instant now = Instant.now();
        ConfigBasedJwtKeyRegistry registry = new ConfigBasedJwtKeyRegistry(
            Optional.empty(),
            Optional.of(Map.of(
                "v1", Map.of(
                    "secret", "v1-secret-at-least-32-bytes-long",
                    "activated-at", now.minus(Duration.ofDays(5)).toString()
                ),
                "v2", Map.of(
                    "secret", "v2-secret-at-least-32-bytes-long",
                    "activated-at", now.minus(Duration.ofHours(1)).toString()
                ),
                "v3", Map.of(
                    "secret", "v3-secret-at-least-32-bytes-long",
                    "activated-at", now.plus(Duration.ofDays(1)).toString()
                )
            ))
        );

        assertEquals("v2", registry.getNewestKey().kid());
    }

    @Test
    void constructor_throwsWhenOnlyFutureKeysConfigured() {
        Instant now = Instant.now();

        assertThrows(IllegalStateException.class, () -> new ConfigBasedJwtKeyRegistry(
            Optional.empty(),
            Optional.of(Map.of(
                "v3", Map.of(
                    "secret", "v3-secret-at-least-32-bytes-long",
                    "activated-at", now.plus(Duration.ofDays(1)).toString()
                )
            ))
        ));
    }

    @Test
    void constructor_allowsMultiKeyConfigWithoutLegacyConfig() {
        ConfigBasedJwtKeyRegistry registry = new ConfigBasedJwtKeyRegistry(
            Optional.empty(),
            Optional.of(Map.of(
                "v1", Map.of(
                    "secret", "v1-secret-at-least-32-bytes-long",
                    "activated-at", Instant.parse("2026-01-01T00:00:00Z").toString()
                )
            ))
        );

        assertEquals("v1", registry.getNewestKey().kid());
    }

    @Test
    void constructor_usesLegacyConfigWhenMultiKeyConfigMissing() {
        ConfigBasedJwtKeyRegistry registry = new ConfigBasedJwtKeyRegistry(
            Optional.of(new TestJwtConfig()),
            Optional.empty()
        );

        assertEquals("legacy-kid", registry.getNewestKey().kid());
    }

    private static final class TestJwtConfig implements JwtConfig {
        @Override
        public String secret() {
            return "legacy-secret-at-least-32-bytes-long";
        }

        @Override
        public String kid() {
            return "legacy-kid";
        }

        @Override
        public int ttlSeconds() {
            return 432000;
        }

        @Override
        public int refreshWindowPercent() {
            return 20;
        }
    }
}
