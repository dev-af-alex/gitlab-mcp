package com.alexaf.gitlabmcp.tool.gitlab;

import com.alexaf.gitlabmcp.application.JsonResponseWriter;
import com.alexaf.gitlabmcp.port.GitlabGateway;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
public class GitlabResources {

    private static final int DEFAULT_RESOURCE_BYTES = 60_000;

    private final GitlabGateway gitlab;
    private final JsonResponseWriter responseWriter;

    public GitlabResources(GitlabGateway gitlab, JsonResponseWriter responseWriter) {
        this.gitlab = gitlab;
        this.responseWriter = responseWriter;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @McpResource(
            name = "gitlab_pipeline_summary",
            title = "GitLab pipeline summary",
            uri = "gitlab://projects/{projectId}/pipelines/{pipelineId}/summary",
            description = "Read a GitLab pipeline summary as JSON. projectId must be a numeric id or URL-encoded path.",
            mimeType = "application/json")
    public String pipelineSummary(String projectId, String pipelineId) {
        return responseWriter.write(gitlab.getPipeline(decode(projectId), pipelineId));
    }

    @McpResource(
            name = "gitlab_job_trace",
            title = "GitLab job trace",
            uri = "gitlab://projects/{projectId}/jobs/{jobId}/trace",
            description = "Read a redacted GitLab job trace. projectId must be a numeric id or URL-encoded path."
    )
    public String jobTrace(String projectId, String jobId) {
        return gitlab.getJobTraceTail(decode(projectId), jobId, DEFAULT_RESOURCE_BYTES);
    }

    @McpResource(
            name = "gitlab_job_artifact_file",
            title = "GitLab job artifact file",
            uri = "gitlab://projects/{projectId}/jobs/{jobId}/artifacts/{artifactPath}",
            description = "Read one redacted text file from GitLab job artifacts. projectId and artifactPath must be URL-encoded when they contain slashes."
    )
    public String jobArtifactFile(String projectId, String jobId, String artifactPath) {
        return gitlab.getJobArtifactFile(
                decode(projectId),
                jobId,
                decode(artifactPath),
                DEFAULT_RESOURCE_BYTES);
    }
}
