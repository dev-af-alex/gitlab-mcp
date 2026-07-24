package com.alexaf.gitlabmcp.tool.gitlab;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.alexaf.gitlabmcp.application.JsonResponseWriter;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.port.GitlabGateway;

@Component
public class GitlabJobTools {

    private final GitlabGateway gitlab;
    private final JsonResponseWriter responseWriter;

    public GitlabJobTools(GitlabGateway gitlab, JsonResponseWriter responseWriter) {
        this.gitlab = gitlab;
        this.responseWriter = responseWriter;
    }

    @McpTool(
            name = "gitlab_get_job_trace",
            description = "Get the redacted tail of a GitLab job trace, optionally truncated by max bytes. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getJobTrace(
            @McpToolParam(description = "Project id or full path.") String projectId,
            @McpToolParam(description = "Job id or GitLab job URL.") String jobId,
            @McpToolParam(description = "Maximum trace bytes to return. Defaults to 60000.", required = false)
                    Integer maxBytes) {
        return jobTraceTail(projectId, jobId, maxBytes);
    }

    @McpTool(
            name = "gitlab_get_job_trace_tail",
            description = "Get the redacted tail of a GitLab job trace by job id or URL. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getJobTraceTail(
            @McpToolParam(description = "Project id or full path.") String projectId,
            @McpToolParam(description = "Job id or GitLab job URL.") String jobId,
            @McpToolParam(description = "Maximum trace bytes to return. Defaults to 60000.", required = false)
                    Integer maxBytes) {
        return jobTraceTail(projectId, jobId, maxBytes);
    }

    @McpTool(
            name = "gitlab_list_job_artifacts",
            description =
                    "List files in GitLab job artifacts, using artifact metadata when the server supports it. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String listJobArtifacts(
            @McpToolParam(description = "Project id or full path.") String projectId,
            @McpToolParam(description = "Job id or GitLab job URL.") String jobId,
            @McpToolParam(description = "Path inside the artifacts archive to browse.", required = false) String path,
            @McpToolParam(description = "Whether to list entries recursively.", required = false) Boolean recursive,
            @McpToolParam(description = "Page number, starting from 1.", required = false) Integer page,
            @McpToolParam(description = "Results per page. Capped by GITLAB_MAX_PER_PAGE.", required = false)
                    Integer perPage) {
        return responseWriter.write(
                gitlab.listJobArtifacts(projectId, jobId, path, recursive, new GitlabPageRequest(page, perPage)));
    }

    @McpTool(
            name = "gitlab_find_job_artifact_files",
            description = "Find file paths in a GitLab job artifacts ZIP archive by glob or regex. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String findJobArtifactFiles(
            @McpToolParam(description = "Project id or full path.") String projectId,
            @McpToolParam(description = "Job id or GitLab job URL.") String jobId,
            @McpToolParam(description = "Glob or regex path pattern. Example: **/TEST-*.xml.") String pattern,
            @McpToolParam(
                            description = "Treat pattern as Java regex instead of glob. Defaults to false.",
                            required = false)
                    Boolean regex,
            @McpToolParam(description = "Page number, starting from 1.", required = false) Integer page,
            @McpToolParam(description = "Results per page. Capped by GITLAB_MAX_PER_PAGE.", required = false)
                    Integer perPage) {
        return responseWriter.write(
                gitlab.findJobArtifactFiles(projectId, jobId, pattern, regex, new GitlabPageRequest(page, perPage)));
    }

    @McpTool(
            name = "gitlab_get_job_artifact_file",
            description =
                    "Get one text file from a GitLab job artifacts archive, redacted and optionally truncated. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getJobArtifactFile(
            @McpToolParam(description = "Project id or full path.") String projectId,
            @McpToolParam(description = "Job id or GitLab job URL.") String jobId,
            @McpToolParam(description = "Path to a text file inside the artifacts archive.") String artifactPath,
            @McpToolParam(description = "Maximum file bytes to return. Defaults to 60000.", required = false)
                    Integer maxBytes) {
        return gitlab.getJobArtifactFile(projectId, jobId, artifactPath, GitlabToolSupport.defaultMaxBytes(maxBytes));
    }

    private String jobTraceTail(String projectId, String jobId, Integer maxBytes) {
        return gitlab.getJobTraceTail(projectId, jobId, GitlabToolSupport.defaultMaxBytes(maxBytes));
    }
}
