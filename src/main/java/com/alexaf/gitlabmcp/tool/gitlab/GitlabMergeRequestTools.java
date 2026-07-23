package com.alexaf.gitlabmcp.tool.gitlab;

import com.alexaf.gitlabmcp.application.JsonResponseWriter;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.domain.MergeRequestQuery;
import com.alexaf.gitlabmcp.port.GitlabGateway;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class GitlabMergeRequestTools {

    private final GitlabGateway gitlab;
    private final JsonResponseWriter responseWriter;

    public GitlabMergeRequestTools(GitlabGateway gitlab, JsonResponseWriter responseWriter) {
        this.gitlab = gitlab;
        this.responseWriter = responseWriter;
    }

    @McpTool(
            name = "gitlab_list_merge_requests",
            description = "List merge requests for a GitLab project. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String listMergeRequests(
            @McpToolParam(description = "Project id or full path.")
            String projectId,
            @McpToolParam(description = "MR state: opened, closed, locked, merged, or all.", required = false)
            String state,
            @McpToolParam(description = "Search text in title or description.", required = false)
            String search,
            @McpToolParam(description = "Source branch name.", required = false)
            String sourceBranch,
            @McpToolParam(description = "Target branch name.", required = false)
            String targetBranch,
            @McpToolParam(description = "Author username.", required = false)
            String authorUsername,
            @McpToolParam(description = "Reviewer username.", required = false)
            String reviewerUsername,
            @McpToolParam(description = "Page number, starting from 1.", required = false)
            Integer page,
            @McpToolParam(description = "Results per page. Capped by GITLAB_MAX_PER_PAGE.", required = false)
            Integer perPage) {
        MergeRequestQuery query = new MergeRequestQuery(
                state,
                search,
                sourceBranch,
                targetBranch,
                authorUsername,
                reviewerUsername,
                new GitlabPageRequest(page, perPage));
        return responseWriter.write(gitlab.listMergeRequests(projectId, query));
    }

    @McpTool(
            name = "gitlab_get_merge_request",
            description = "Get one merge request by project and MR IID. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getMergeRequest(
            @McpToolParam(description = "Project id or full path.")
            String projectId,
            @McpToolParam(description = "Merge request IID, for example 2115, !2115, or a GitLab merge request URL.")
            String mergeRequestIid) {
        return responseWriter.write(gitlab.getMergeRequest(projectId, mergeRequestIid));
    }

    @McpTool(
            name = "gitlab_get_merge_request_changes",
            description = "Get changed files and diffs for a merge request. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getMergeRequestChanges(
            @McpToolParam(description = "Project id or full path.")
            String projectId,
            @McpToolParam(description = "Merge request IID, for example 2115, !2115, or a GitLab merge request URL.")
            String mergeRequestIid) {
        return responseWriter.write(gitlab.getMergeRequestChanges(projectId, mergeRequestIid));
    }

    @McpTool(
            name = "gitlab_get_merge_request_commits",
            description = "Get commits included in a merge request. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getMergeRequestCommits(
            @McpToolParam(description = "Project id or full path.")
            String projectId,
            @McpToolParam(description = "Merge request IID, for example 2115, !2115, or a GitLab merge request URL.")
            String mergeRequestIid,
            @McpToolParam(description = "Page number, starting from 1.", required = false)
            Integer page,
            @McpToolParam(description = "Results per page. Capped by GITLAB_MAX_PER_PAGE.", required = false)
            Integer perPage) {
        return responseWriter.write(gitlab.listMergeRequestCommits(
                projectId,
                mergeRequestIid,
                new GitlabPageRequest(page, perPage)));
    }

    @McpTool(
            name = "gitlab_get_merge_request_discussions",
            description = "Get discussion threads for a merge request. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getMergeRequestDiscussions(
            @McpToolParam(description = "Project id or full path.")
            String projectId,
            @McpToolParam(description = "Merge request IID, for example 2115, !2115, or a GitLab merge request URL.")
            String mergeRequestIid,
            @McpToolParam(description = "Page number, starting from 1.", required = false)
            Integer page,
            @McpToolParam(description = "Results per page. Capped by GITLAB_MAX_PER_PAGE.", required = false)
            Integer perPage) {
        return responseWriter.write(gitlab.listMergeRequestDiscussions(
                projectId,
                mergeRequestIid,
                new GitlabPageRequest(page, perPage)));
    }

    @McpTool(
            name = "gitlab_get_merge_request_pipelines",
            description = "Get pipelines related to a merge request. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getMergeRequestPipelines(
            @McpToolParam(description = "Project id or full path.")
            String projectId,
            @McpToolParam(description = "Merge request IID, for example 2115, !2115, or a GitLab merge request URL.")
            String mergeRequestIid,
            @McpToolParam(description = "Page number, starting from 1.", required = false)
            Integer page,
            @McpToolParam(description = "Results per page. Capped by GITLAB_MAX_PER_PAGE.", required = false)
            Integer perPage) {
        return responseWriter.write(gitlab.listMergeRequestPipelines(
                projectId,
                mergeRequestIid,
                new GitlabPageRequest(page, perPage)));
    }
}
