package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitlabTestCase(
        String status,
        String name,
        String classname,
        @JsonProperty("execution_time") Double executionTime,
        @JsonProperty("system_output") String systemOutput,
        @JsonProperty("stack_trace") String stackTrace
) {
}
