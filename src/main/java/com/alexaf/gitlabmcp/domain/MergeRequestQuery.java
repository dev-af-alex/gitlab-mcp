package com.alexaf.gitlabmcp.domain;

public record MergeRequestQuery(
        String state,
        String search,
        String sourceBranch,
        String targetBranch,
        String authorUsername,
        String reviewerUsername,
        GitlabPageRequest page) {}
