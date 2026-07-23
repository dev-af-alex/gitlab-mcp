package com.alexaf.gitlabmcp.tool.gitlab;

import com.alexaf.gitlabmcp.application.JsonResponseWriter;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.alexaf.gitlabmcp.port.GitlabGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitlabResourcesTest {

    private GitlabGateway gitlab;
    private JsonResponseWriter responseWriter;
    private GitlabResources resources;

    @BeforeEach
    void setUp() {
        gitlab = mock(GitlabGateway.class);
        responseWriter = mock(JsonResponseWriter.class);
        resources = new GitlabResources(gitlab, responseWriter);
    }

    @Test
    void pipelineSummaryDelegatesDecodedResourceVariablesToGateway() {
        Pipeline pipeline = new Pipeline(
                123L, 1L, 11L, "abc123", "main", "failed", "push",
                null, null, null, null, 60L, 1L,
                "https://gitlab.example/pipelines/123");
        when(gitlab.getPipeline("group/repo", "pipeline-url")).thenReturn(pipeline);
        when(responseWriter.write(pipeline)).thenReturn("json");

        String response = resources.pipelineSummary("group%2Frepo", "pipeline-url");

        assertThat(response).isEqualTo("json");
        verify(gitlab).getPipeline("group/repo", "pipeline-url");
        verify(responseWriter).write(pipeline);
    }

    @Test
    void jobTraceDelegatesToGateway() {
        when(gitlab.getJobTraceTail("group/repo", "job-url", 60_000))
                .thenReturn("trace");

        String response = resources.jobTrace("group%2Frepo", "job-url");

        assertThat(response).isEqualTo("trace");
        verify(gitlab).getJobTraceTail("group/repo", "job-url", 60_000);
    }

    @Test
    void jobArtifactFileDecodesArtifactPathAndDelegatesToGateway() {
        when(gitlab.getJobArtifactFile(
                "group/repo",
                "job-url",
                "target/surefire-reports/TEST.xml",
                60_000))
                .thenReturn("artifact");

        String response = resources.jobArtifactFile(
                "group%2Frepo",
                "job-url",
                "target%2Fsurefire-reports%2FTEST.xml");

        assertThat(response).isEqualTo("artifact");
        verify(gitlab).getJobArtifactFile(
                "group/repo",
                "job-url",
                "target/surefire-reports/TEST.xml",
                60_000);
    }
}
