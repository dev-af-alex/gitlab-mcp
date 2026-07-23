package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiffRefs(
        @JsonProperty("base_sha") String baseSha,
        @JsonProperty("head_sha") String headSha,
        @JsonProperty("start_sha") String startSha
) {
}
