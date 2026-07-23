package com.alexaf.gitlabmcp.tool.gitlab;

import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.dto.Commit;
import com.alexaf.gitlabmcp.gitlab.dto.Discussion;
import com.alexaf.gitlabmcp.gitlab.dto.MergeRequest;
import com.alexaf.gitlabmcp.gitlab.dto.MergeRequestChanges;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class GitlabMergeRequestTools {

    private final GitlabApiClient gitlab;

    public GitlabMergeRequestTools(GitlabApiClient gitlab) {
        this.gitlab = gitlab;
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
        String projectPath = gitlab.projectPath(projectId);
        return gitlab.json(gitlab.getList("/projects/" + projectPath + "/merge_requests", MergeRequest.class,
                gitlab.param("state", gitlab.mergeRequestState(state)),
                gitlab.param("search", search),
                gitlab.param("source_branch", sourceBranch),
                gitlab.param("target_branch", targetBranch),
                gitlab.param("author_username", authorUsername),
                gitlab.param("reviewer_username", reviewerUsername),
                gitlab.param("order_by", "updated_at"),
                gitlab.param("sort", "desc"),
                gitlab.param("page", gitlab.page(page)),
                gitlab.param("per_page", gitlab.perPage(perPage))));
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
        long iid = gitlab.mergeRequestIid(mergeRequestIid);
        return gitlab.json(gitlab.getObject("/projects/" + gitlab.projectPath(projectId) + "/merge_requests/" + iid,
                MergeRequest.class));
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
        long iid = gitlab.mergeRequestIid(mergeRequestIid);
        return gitlab.json(gitlab.getObject("/projects/" + gitlab.projectPath(projectId) + "/merge_requests/" + iid + "/changes",
                MergeRequestChanges.class));
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
        long iid = gitlab.mergeRequestIid(mergeRequestIid);
        return gitlab.json(gitlab.getList("/projects/" + gitlab.projectPath(projectId) + "/merge_requests/" + iid + "/commits",
                Commit.class,
                gitlab.param("page", gitlab.page(page)),
                gitlab.param("per_page", gitlab.perPage(perPage))));
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
        long iid = gitlab.mergeRequestIid(mergeRequestIid);
        return gitlab.json(gitlab.getList("/projects/" + gitlab.projectPath(projectId) + "/merge_requests/" + iid + "/discussions",
                Discussion.class,
                gitlab.param("page", gitlab.page(page)),
                gitlab.param("per_page", gitlab.perPage(perPage))));
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
        long iid = gitlab.mergeRequestIid(mergeRequestIid);
        return gitlab.json(gitlab.getList("/projects/" + gitlab.projectPath(projectId) + "/merge_requests/" + iid + "/pipelines",
                Pipeline.class,
                gitlab.param("page", gitlab.page(page)),
                gitlab.param("per_page", gitlab.perPage(perPage))));
    }
}
