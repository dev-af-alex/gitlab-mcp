package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserSummary(
        Long id,
        String username,
        String name,
        String state,
        @JsonProperty("web_url") String webUrl,
        @JsonProperty("avatar_url") String avatarUrl
) {
}
