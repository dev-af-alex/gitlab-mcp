package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectNamespace(
        Long id,
        String name,
        String path,
        String kind,
        @JsonProperty("full_path") String fullPath,
        @JsonProperty("web_url") String webUrl
) {
}
