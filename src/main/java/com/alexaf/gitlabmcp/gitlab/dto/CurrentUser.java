package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentUser(
        Long id,
        String username,
        String name,
        String state,
        @JsonProperty("web_url") String webUrl,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("public_email") String publicEmail,
        String email,
        @JsonProperty("last_activity_on") String lastActivityOn
) {
}
