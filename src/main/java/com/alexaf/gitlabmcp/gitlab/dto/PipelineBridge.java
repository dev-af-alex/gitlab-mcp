package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PipelineBridge(
        Long id,
        String name,
        String stage,
        String status,
        @JsonProperty("downstream_pipeline") Pipeline downstreamPipeline) {}
