package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Commit(
        String id,
        @JsonProperty("short_id") String shortId,
        String title,
        String message,
        @JsonProperty("author_name") String authorName,
        @JsonProperty("author_email") String authorEmail,
        @JsonProperty("authored_date") String authoredDate,
        @JsonProperty("committer_name") String committerName,
        @JsonProperty("committed_date") String committedDate,
        @JsonProperty("web_url") String webUrl) {}
