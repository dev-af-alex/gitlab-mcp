package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Pipeline(
        Long id,
        Long iid,
        @JsonProperty("project_id") Long projectId,
        String sha,
        String ref,
        String status,
        String source,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("started_at") String startedAt,
        @JsonProperty("finished_at") String finishedAt,
        Long duration,
        @JsonProperty("queued_duration") Long queuedDuration,
        @JsonProperty("web_url") String webUrl) {}
