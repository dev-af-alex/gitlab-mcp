package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Job(
        Long id,
        String name,
        String stage,
        String status,
        @JsonProperty("failure_reason") String failureReason,
        @JsonProperty("web_url") String webUrl,
        String ref,
        Boolean tag,
        @JsonProperty("allow_failure") Boolean allowFailure,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("started_at") String startedAt,
        @JsonProperty("finished_at") String finishedAt,
        Double duration,
        @JsonProperty("queued_duration") Double queuedDuration,
        List<JobArtifact> artifacts
) {
}
