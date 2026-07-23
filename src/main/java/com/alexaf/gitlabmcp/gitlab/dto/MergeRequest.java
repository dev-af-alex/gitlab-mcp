package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MergeRequest(
        Long id,
        Long iid,
        @JsonProperty("project_id") Long projectId,
        String title,
        String description,
        String state,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("target_branch") String targetBranch,
        @JsonProperty("source_branch") String sourceBranch,
        UserSummary author,
        List<UserSummary> assignees,
        List<UserSummary> reviewers,
        List<String> labels,
        Boolean draft,
        @JsonProperty("work_in_progress") Boolean workInProgress,
        @JsonProperty("user_notes_count") Integer userNotesCount,
        String sha,
        String reference,
        MergeRequestReferences references,
        @JsonProperty("web_url") String webUrl,
        @JsonProperty("merge_status") String mergeStatus,
        @JsonProperty("detailed_merge_status") String detailedMergeStatus,
        @JsonProperty("has_conflicts") Boolean hasConflicts,
        @JsonProperty("blocking_discussions_resolved") Boolean blockingDiscussionsResolved,
        Pipeline pipeline,
        @JsonProperty("head_pipeline") Pipeline headPipeline,
        @JsonProperty("diff_refs") DiffRefs diffRefs
) {
}
