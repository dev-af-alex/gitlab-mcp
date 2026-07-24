package com.alexaf.gitlabmcp.tool.gitlab;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.alexaf.gitlabmcp.application.JsonResponseWriter;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.port.GitlabGateway;

@Component
public class GitlabPipelineTools {

    private final GitlabGateway gitlab;
    private final JsonResponseWriter responseWriter;

    public GitlabPipelineTools(GitlabGateway gitlab, JsonResponseWriter responseWriter) {
        this.gitlab = gitlab;
        this.responseWriter = responseWriter;
    }

    @McpTool(
            name = "gitlab_get_pipeline",
            description = "Get one GitLab pipeline by numeric pipeline id. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getPipeline(
            @McpToolParam(description = "Project id or full path.") String projectId,
            @McpToolParam(description = "Pipeline id or GitLab pipeline URL.") String pipelineId) {
        return responseWriter.write(gitlab.getPipeline(projectId, pipelineId));
    }

    @McpTool(
            name = "gitlab_list_pipeline_jobs",
            description = "List jobs for a GitLab pipeline. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String listPipelineJobs(
            @McpToolParam(description = "Project id or full path.") String projectId,
            @McpToolParam(description = "Pipeline id or GitLab pipeline URL.") String pipelineId,
            @McpToolParam(description = "Include retried jobs.", required = false) Boolean includeRetried,
            @McpToolParam(description = "Page number, starting from 1.", required = false) Integer page,
            @McpToolParam(description = "Results per page. Capped by GITLAB_MAX_PER_PAGE.", required = false)
                    Integer perPage) {
        return responseWriter.write(
                gitlab.listPipelineJobs(projectId, pipelineId, includeRetried, new GitlabPageRequest(page, perPage)));
    }
}
