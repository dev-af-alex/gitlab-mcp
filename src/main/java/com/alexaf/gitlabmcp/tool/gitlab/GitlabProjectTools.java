package com.alexaf.gitlabmcp.tool.gitlab;

import com.alexaf.gitlabmcp.application.JsonResponseWriter;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.port.GitlabGateway;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class GitlabProjectTools {

    private final GitlabGateway gitlab;
    private final JsonResponseWriter responseWriter;

    public GitlabProjectTools(GitlabGateway gitlab, JsonResponseWriter responseWriter) {
        this.gitlab = gitlab;
        this.responseWriter = responseWriter;
    }

    @McpTool(
            name = "gitlab_get_current_user",
            description = "Get the GitLab user associated with the configured token.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getCurrentUser() {
        return responseWriter.write(gitlab.getCurrentUser());
    }

    @McpTool(
            name = "gitlab_get_server_info",
            description = "Get the connected GitLab version and supported API capabilities.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getServerInfo() {
        return responseWriter.write(gitlab.getServerInfo());
    }

    @McpTool(
            name = "gitlab_search_projects",
            description = "Search visible GitLab projects. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String searchProjects(
            @McpToolParam(description = "Search text, for example project name or path.")
            String search,
            @McpToolParam(description = "Page number, starting from 1.", required = false)
            Integer page,
            @McpToolParam(description = "Results per page. Capped by GITLAB_MAX_PER_PAGE.", required = false)
            Integer perPage) {
        return responseWriter.write(gitlab.searchProjects(search, new GitlabPageRequest(page, perPage)));
    }

    @McpTool(
            name = "gitlab_get_project",
            description = "Get a GitLab project by numeric id or path. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getProject(
            @McpToolParam(description = "Project id or full path, for example group/subgroup/project.")
            String projectId) {
        return responseWriter.write(gitlab.getProject(projectId));
    }
}
