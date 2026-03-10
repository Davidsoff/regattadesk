package com.regattadesk.adjudication.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdjudicationEntryDetailResponse(
    @JsonProperty("entry") EntrySummary entry,
    @JsonProperty("investigations") List<InvestigationSummary> investigations,
    @JsonProperty("history") List<HistoryItem> history,
    @JsonProperty("revision_impact") RevisionImpact revisionImpact
) {
    public record EntrySummary(
        @JsonProperty("entry_id") UUID entryId,
        @JsonProperty("crew_name") String crewName,
        @JsonProperty("status") String status,
        @JsonProperty("result_label") String resultLabel,
        @JsonProperty("penalty_seconds") Integer penaltySeconds
    ) {
    }

    public record InvestigationSummary(
        @JsonProperty("investigation_id") UUID investigationId,
        @JsonProperty("entry_id") UUID entryId,
        @JsonProperty("crew_name") String crewName,
        @JsonProperty("status") String status,
        @JsonProperty("description") String description,
        @JsonProperty("outcome") String outcome,
        @JsonProperty("penalty_seconds") Integer penaltySeconds,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("closed_at") Instant closedAt
    ) {
    }

    public record HistoryItem(
        @JsonProperty("action") String action,
        @JsonProperty("reason") String reason,
        @JsonProperty("note") String note,
        @JsonProperty("actor") String actor,
        @JsonProperty("previous_status") String previousStatus,
        @JsonProperty("current_status") String currentStatus,
        @JsonProperty("previous_result_label") String previousResultLabel,
        @JsonProperty("current_result_label") String currentResultLabel,
        @JsonProperty("penalty_seconds") Integer penaltySeconds,
        @JsonProperty("results_revision") int resultsRevision,
        @JsonProperty("created_at") Instant createdAt
    ) {
    }

    public record RevisionImpact(
        @JsonProperty("current_results_revision") int currentResultsRevision,
        @JsonProperty("next_results_revision") int nextResultsRevision,
        @JsonProperty("message") String message
    ) {
    }
}
