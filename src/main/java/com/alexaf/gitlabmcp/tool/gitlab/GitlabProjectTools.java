package com.alexaf.gitlabmcp.tool.gitlab;

import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.dto.CurrentUser;
import com.alexaf.gitlabmcp.gitlab.dto.Project;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class GitlabProjectTools {

    private final GitlabApiClient gitlab;

    public GitlabProjectTools(GitlabApiClient gitlab) {
        this.gitlab = gitlab;
    }

    @McpTool(
            name = "gitlab_get_current_user",
            description = "Get the GitLab user associated with the configured token.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getCurrentUser() {
        return gitlab.json(gitlab.getObject("/user", CurrentUser.class));
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
        return gitlab.json(gitlab.getList("/projects", Project.class,
                gitlab.param("search", search),
                gitlab.param("membership", true),
                gitlab.param("page", gitlab.page(page)),
                gitlab.param("per_page", gitlab.perPage(perPage))));
    }

    @McpTool(
            name = "gitlab_get_project",
            description = "Get a GitLab project by numeric id or path. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getProject(
            @McpToolParam(description = "Project id or full path, for example group/subgroup/project.")
            String projectId) {
        return gitlab.json(gitlab.getObject("/projects/" + gitlab.projectPath(projectId), Project.class));
    }
}
