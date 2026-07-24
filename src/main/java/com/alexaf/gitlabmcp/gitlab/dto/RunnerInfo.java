package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RunnerInfo(
        Long id,
        String description,
        Boolean active,
        Boolean paused,
        @JsonProperty("is_shared") Boolean shared,
        @JsonProperty("runner_type") String runnerType,
        String name,
        Boolean online,
        String status) {}
