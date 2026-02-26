package com.regattadesk.public_api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for public versions endpoint.
 * 
 * Contains current draw and results revisions for a regatta.
 * Used by public clients to discover the current version tuple and initialize immutable URL routing.
 */
public record PublicVersionsResponse(
    @JsonProperty("draw_revision")
    int drawRevision,
    
    @JsonProperty("results_revision")
    int resultsRevision
) {}
