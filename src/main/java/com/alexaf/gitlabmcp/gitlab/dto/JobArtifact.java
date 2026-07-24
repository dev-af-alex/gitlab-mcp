package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobArtifact(
        @JsonProperty("file_type") String fileType,
        Long size,
        String filename,
        @JsonProperty("file_format") String fileFormat) {}
