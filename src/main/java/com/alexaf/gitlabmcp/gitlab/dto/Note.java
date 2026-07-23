package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Note(
        Long id,
        String type,
        String body,
        UserSummary author,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        Boolean system,
        Boolean resolvable,
        Boolean resolved,
        @JsonProperty("resolved_by") UserSummary resolvedBy
) {
}
