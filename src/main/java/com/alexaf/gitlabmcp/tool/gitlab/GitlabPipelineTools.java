package com.alexaf.gitlabmcp.tool.gitlab;

import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class GitlabPipelineTools {

    private final GitlabApiClient gitlab;

    public GitlabPipelineTools(GitlabApiClient gitlab) {
        this.gitlab = gitlab;
    }

    @McpTool(
            name = "gitlab_get_pipeline",
            description = "Get one GitLab pipeline by numeric pipeline id. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getPipeline(
            @McpToolParam(description = "Project id or full path.")
            String projectId,
            @McpToolParam(description = "Pipeline id or GitLab pipeline URL.")
            String pipelineId) {
        long id = gitlab.pipelineId(pipelineId);
        return gitlab.json(gitlab.getObject("/projects/" + gitlab.projectPath(projectId) + "/pipelines/" + id,
                Pipeline.class));
    }

    @McpTool(
            name = "gitlab_list_pipeline_jobs",
            description = "List jobs for a GitLab pipeline. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String listPipelineJobs(
            @McpToolParam(description = "Project id or full path.")
            String projectId,
            @McpToolParam(description = "Pipeline id or GitLab pipeline URL.")
            String pipelineId,
            @McpToolParam(description = "Include retried jobs.", required = false)
            Boolean includeRetried,
            @McpToolParam(description = "Page number, starting from 1.", required = false)
            Integer page,
            @McpToolParam(description = "Results per page. Capped by GITLAB_MAX_PER_PAGE.", required = false)
            Integer perPage) {
        long id = gitlab.pipelineId(pipelineId);
        return gitlab.json(gitlab.getList("/projects/" + gitlab.projectPath(projectId) + "/pipelines/" + id + "/jobs",
                Job.class,
                gitlab.param("include_retried", includeRetried),
                gitlab.param("page", gitlab.page(page)),
                gitlab.param("per_page", gitlab.perPage(perPage))));
    }
}
