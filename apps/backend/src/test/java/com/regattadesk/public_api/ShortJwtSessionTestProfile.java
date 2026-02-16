package com.regattadesk.public_api;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class ShortJwtSessionTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "jwt.public.ttl-seconds", "2",
            "jwt.public.refresh-window-percent", "100"
        );
    }
}
