package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MergeRequestChanges(
        Long id,
        Long iid,
        @JsonProperty("project_id") Long projectId,
        String title,
        String description,
        String state,
        @JsonProperty("target_branch") String targetBranch,
        @JsonProperty("source_branch") String sourceBranch,
        @JsonProperty("web_url") String webUrl,
        @JsonProperty("diff_refs") DiffRefs diffRefs,
        List<FileChange> changes
) {
}
