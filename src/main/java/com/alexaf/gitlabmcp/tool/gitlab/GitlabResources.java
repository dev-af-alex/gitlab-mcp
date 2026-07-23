package com.alexaf.gitlabmcp.tool.gitlab;

import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
public class GitlabResources {

    private static final int DEFAULT_RESOURCE_BYTES = 60_000;

    private final GitlabApiClient gitlab;

    public GitlabResources(GitlabApiClient gitlab) {
        this.gitlab = gitlab;
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
        String projectPath = gitlab.projectPath(projectId);
        long id = gitlab.pipelineId(pipelineId);
        return gitlab.json(gitlab.getObject("/projects/" + projectPath + "/pipelines/" + id, Pipeline.class));
    }

    @McpResource(
            name = "gitlab_job_trace",
            title = "GitLab job trace",
            uri = "gitlab://projects/{projectId}/jobs/{jobId}/trace",
            description = "Read a redacted GitLab job trace. projectId must be a numeric id or URL-encoded path."
    )
    public String jobTrace(String projectId, String jobId) {
        String projectPath = gitlab.projectPath(projectId);
        long id = gitlab.jobId(jobId);
        return gitlab.getTailText("/projects/" + projectPath + "/jobs/" + id + "/trace", DEFAULT_RESOURCE_BYTES);
    }

    @McpResource(
            name = "gitlab_job_artifact_file",
            title = "GitLab job artifact file",
            uri = "gitlab://projects/{projectId}/jobs/{jobId}/artifacts/{artifactPath}",
            description = "Read one redacted text file from GitLab job artifacts. projectId and artifactPath must be URL-encoded when they contain slashes."
    )
    public String jobArtifactFile(String projectId, String jobId, String artifactPath) {
        String projectPath = gitlab.projectPath(projectId);
        long id = gitlab.jobId(jobId);
        return gitlab.getLimitedText("/projects/" + projectPath + "/jobs/" + id
                + "/artifacts/" + decode(artifactPath), DEFAULT_RESOURCE_BYTES);
    }
}
