package com.alexaf.gitlabmcp.tool.gitlab;

import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitlabResourcesTest {

    private RecordingGitlabApiClient gitlab;
    private GitlabResources resources;

    private static Pipeline pipeline(Long id) {
        return new Pipeline(id, 1L, 11L, "abc123", "main", "failed", "push",
                null, null, null, null, 60L, 1L, "https://gitlab.example/pipelines/" + id);
    }

    @BeforeEach
    void setUp() {
        gitlab = new RecordingGitlabApiClient();
        resources = new GitlabResources(gitlab);
    }

    @Test
    void pipelineSummaryMapsResourceVariablesToPipelineEndpoint() {
        Pipeline pipeline = pipeline(123L);
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.pipelineIdReturn = 123L;
        gitlab.objectResponse = pipeline;

        String response = resources.pipelineSummary("group%2Frepo", "pipeline-url");

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.projectIdInput).isEqualTo("group%2Frepo");
        assertThat(gitlab.pipelineIdInput).isEqualTo("pipeline-url");
        assertThat(gitlab.objectPath).isEqualTo("/projects/group%2Frepo/pipelines/123");
        assertThat(gitlab.jsonInput).isSameAs(pipeline);
    }

    @Test
    void jobTraceMapsResourceVariablesToTraceEndpoint() {
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.jobIdReturn = 8L;
        gitlab.textResponse = "trace";

        String response = resources.jobTrace("group%2Frepo", "job-url");

        assertThat(response).isEqualTo("trace");
        assertThat(gitlab.jobIdInput).isEqualTo("job-url");
        assertThat(gitlab.tailPath).isEqualTo("/projects/group%2Frepo/jobs/8/trace");
        assertThat(gitlab.tailMaxBytes).isEqualTo(60_000);
    }

    @Test
    void jobArtifactFileDecodesArtifactPathAndMapsToArtifactEndpoint() {
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.jobIdReturn = 8L;
        gitlab.textResponse = "artifact";

        String response = resources.jobArtifactFile("group%2Frepo", "job-url", "target%2Fsurefire-reports%2FTEST.xml");

        assertThat(response).isEqualTo("artifact");
        assertThat(gitlab.textPath).isEqualTo("/projects/group%2Frepo/jobs/8/artifacts/target/surefire-reports/TEST.xml");
        assertThat(gitlab.maxBytes).isEqualTo(60_000);
    }

    private static final class RecordingGitlabApiClient extends GitlabApiClient {

        private Object objectResponse;
        private String textResponse;
        private Object jsonInput;
        private String projectPathReturn = "project";
        private String projectIdInput;
        private long pipelineIdReturn = 1L;
        private String pipelineIdInput;
        private long jobIdReturn = 1L;
        private String jobIdInput;
        private String objectPath;
        private String textPath;
        private Integer maxBytes;
        private String tailPath;
        private Integer tailMaxBytes;

        private RecordingGitlabApiClient() {
            super(new GitlabProperties("https://gitlab.example", "token", List.of(), 20, 100),
                    new ObjectMapper(), RestClient.builder());
        }

        @Override
        public String projectPath(String projectId) {
            projectIdInput = projectId;
            return projectPathReturn;
        }

        @Override
        public long pipelineId(String value) {
            pipelineIdInput = value;
            return pipelineIdReturn;
        }

        @Override
        public long jobId(String value) {
            jobIdInput = value;
            return jobIdReturn;
        }

        @Override
        public <T> T getObject(String path, Class<T> type, QueryParam... queryParams) {
            objectPath = path;
            return type.cast(objectResponse);
        }

        @Override
        public String getLimitedText(String path, Integer maxBytes, QueryParam... queryParams) {
            textPath = path;
            this.maxBytes = maxBytes;
            return textResponse;
        }

        @Override
        public String getTailText(String path, Integer maxBytes, QueryParam... queryParams) {
            tailPath = path;
            tailMaxBytes = maxBytes;
            return textResponse;
        }

        @Override
        public String json(Object value) {
            jsonInput = value;
            return "json";
        }
    }
}
