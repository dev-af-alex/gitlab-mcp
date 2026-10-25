package com.alexaf.gitlabmcp.gitlab.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MergeRequestDiffVersion(
        Long id,
        @JsonProperty("base_commit_sha") String baseSha,
        @JsonProperty("start_commit_sha") String startSha,
        @JsonProperty("head_commit_sha") String headSha,
        String state,
        List<FileChange> diffs) {}
