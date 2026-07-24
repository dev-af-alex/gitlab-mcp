package com.alexaf.gitlabmcp.tool.gitlab;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alexaf.gitlabmcp.application.JsonResponseWriter;
import com.alexaf.gitlabmcp.application.pipeline.PipelineAnalysisEngine;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.domain.GitlabServerInfo;
import com.alexaf.gitlabmcp.domain.GitlabVersion;
import com.alexaf.gitlabmcp.domain.MergeRequestQuery;
import com.alexaf.gitlabmcp.gitlab.diagnostics.ArtifactHintDetector;
import com.alexaf.gitlabmcp.gitlab.diagnostics.LogMatcher;
import com.alexaf.gitlabmcp.gitlab.diagnostics.MavenFailureAnalyzer;
import com.alexaf.gitlabmcp.gitlab.diagnostics.PipelineDiagnosticsResult;
import com.alexaf.gitlabmcp.gitlab.diagnostics.PipelineDiagnosticsService;
import com.alexaf.gitlabmcp.gitlab.diagnostics.SurefireReportAnalyzer;
import com.alexaf.gitlabmcp.gitlab.diagnostics.TraceAnalyzer;
import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;
import com.alexaf.gitlabmcp.gitlab.dto.Commit;
import com.alexaf.gitlabmcp.gitlab.dto.CurrentUser;
import com.alexaf.gitlabmcp.gitlab.dto.Discussion;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.MergeRequest;
import com.alexaf.gitlabmcp.gitlab.dto.MergeRequestChanges;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.alexaf.gitlabmcp.gitlab.dto.Project;
import com.alexaf.gitlabmcp.port.GitlabGateway;
import com.alexaf.gitlabmcp.port.PipelineContextCollector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitlabToolComponentsTest {

    private GitlabGateway gateway;
    private JsonResponseWriter responseWriter;
    private RecordingPipelineDiagnosticsService diagnosticsService;
    private GitlabProjectTools projectTools;
    private GitlabMergeRequestTools mergeRequestTools;
    private GitlabPipelineTools pipelineTools;
    private GitlabJobTools jobTools;
    private GitlabDiagnosticsTools diagnosticsTools;

    private static Project project(Long id, String name) {
        return new Project(
                id,
                null,
                name,
                null,
                null,
                "group/" + name,
                "main",
                null,
                null,
                null,
                null,
                "private",
                false,
                null,
                null);
    }

    private static Pipeline pipeline(Long id) {
        return new Pipeline(
                id,
                1L,
                11L,
                "abc123",
                "main",
                "failed",
                "push",
                null,
                null,
                null,
                null,
                60L,
                1L,
                "https://gitlab.example/pipelines/" + id);
    }

    private static Job job(Long id, String name) {
        return new Job(
                id,
                name,
                "test",
                "failed",
                "script_failure",
                null,
                "main",
                false,
                false,
                null,
                null,
                null,
                60.0,
                1.0,
                List.of());
    }

    private static MergeRequest mergeRequest(Long iid) {
        return new MergeRequest(
                1L, iid, 11L, "MR", null, "opened", null, null, "main", "feature", null, List.of(), List.of(),
                List.of(), false, false, 0, null, null, null, null, null, null, false, true, null, null, null);
    }

    @BeforeEach
    void setUp() {
        gateway = mock(GitlabGateway.class);
        responseWriter = mock(JsonResponseWriter.class);
        when(responseWriter.write(any())).thenReturn("json");
        diagnosticsService = new RecordingPipelineDiagnosticsService(gateway);
        projectTools = new GitlabProjectTools(gateway, responseWriter);
        mergeRequestTools = new GitlabMergeRequestTools(gateway, responseWriter);
        pipelineTools = new GitlabPipelineTools(gateway, responseWriter);
        jobTools = new GitlabJobTools(gateway, responseWriter);
        diagnosticsTools = new GitlabDiagnosticsTools(responseWriter, diagnosticsService);
    }

    @Test
    void getCurrentUserCallsUserEndpointAndSerializesResponse() {
        CurrentUser user = new CurrentUser(7L, "alice", "Alice", "active", null, null, null, null, null);
        when(gateway.getCurrentUser()).thenReturn(user);

        String response = projectTools.getCurrentUser();

        assertThat(response).isEqualTo("json");
        verify(gateway).getCurrentUser();
        verify(responseWriter).write(user);
    }

    @Test
    void getServerInfoDelegatesToGatewayAndSerializesResponse() {
        GitlabServerInfo info = new GitlabServerInfo(
                GitlabVersion.parse("15.1.0-ee"), "abc123", java.util.Set.of(), GitlabVersion.of(15, 1, 0), true);
        when(gateway.getServerInfo()).thenReturn(info);

        String response = projectTools.getServerInfo();

        assertThat(response).isEqualTo("json");
        verify(gateway).getServerInfo();
        verify(responseWriter).write(info);
    }

    @Test
    void searchProjectsMapsPaginationAndSearchToProjectsEndpoint() {
        List<Project> projects = List.of(project(11L, "demo"));
        when(gateway.searchProjects("demo", new GitlabPageRequest(2, 50))).thenReturn(projects);

        String response = projectTools.searchProjects("demo", 2, 50);

        assertThat(response).isEqualTo("json");
        verify(gateway).searchProjects("demo", new GitlabPageRequest(2, 50));
        verify(responseWriter).write(projects);
    }

    @Test
    void getProjectEncodesProjectIdBeforeCallingProjectEndpoint() {
        Project project = project(11L, "repo");
        when(gateway.getProject("group/repo")).thenReturn(project);

        String response = projectTools.getProject("group/repo");

        assertThat(response).isEqualTo("json");
        verify(gateway).getProject("group/repo");
        verify(responseWriter).write(project);
    }

    @Test
    void listMergeRequestsMapsFiltersSortingAndPagination() {
        List<MergeRequest> mergeRequests = List.of();
        MergeRequestQuery query =
                new MergeRequestQuery("open", "bug", "feature", "main", "alice", "bob", new GitlabPageRequest(3, 25));
        when(gateway.listMergeRequests("group/repo", query)).thenReturn(mergeRequests);

        String response = mergeRequestTools.listMergeRequests(
                "group/repo", "open", "bug", "feature", "main", "alice", "bob", 3, 25);

        assertThat(response).isEqualTo("json");
        verify(gateway).listMergeRequests("group/repo", query);
        verify(responseWriter).write(mergeRequests);
    }

    @Test
    void getMergeRequestMapsProjectAndIidToEndpoint() {
        MergeRequest mergeRequest = mergeRequest(42L);
        when(gateway.getMergeRequest("group/repo", "!42")).thenReturn(mergeRequest);

        String response = mergeRequestTools.getMergeRequest("group/repo", "!42");

        assertThat(response).isEqualTo("json");
        verify(gateway).getMergeRequest("group/repo", "!42");
        verify(responseWriter).write(mergeRequest);
    }

    @Test
    void getMergeRequestChangesMapsProjectAndIidToChangesEndpoint() {
        MergeRequestChanges changes =
                new MergeRequestChanges(1L, 42L, 11L, "MR", null, "opened", "main", "feature", null, null, List.of());
        when(gateway.getMergeRequestChanges("group/repo", "!42")).thenReturn(changes);

        String response = mergeRequestTools.getMergeRequestChanges("group/repo", "!42");

        assertThat(response).isEqualTo("json");
        verify(gateway).getMergeRequestChanges("group/repo", "!42");
        verify(responseWriter).write(changes);
    }

    @Test
    void getMergeRequestCommitsMapsProjectIidAndPagination() {
        List<Commit> commits = List.of();
        when(gateway.listMergeRequestCommits("group/repo", "!42", new GitlabPageRequest(4, 30)))
                .thenReturn(commits);

        String response = mergeRequestTools.getMergeRequestCommits("group/repo", "!42", 4, 30);

        assertThat(response).isEqualTo("json");
        verify(gateway).listMergeRequestCommits("group/repo", "!42", new GitlabPageRequest(4, 30));
        verify(responseWriter).write(commits);
    }

    @Test
    void getMergeRequestDiscussionsMapsProjectIidAndPagination() {
        List<Discussion> discussions = List.of();
        when(gateway.listMergeRequestDiscussions("group/repo", "!42", new GitlabPageRequest(5, 40)))
                .thenReturn(discussions);

        String response = mergeRequestTools.getMergeRequestDiscussions("group/repo", "!42", 5, 40);

        assertThat(response).isEqualTo("json");
        verify(gateway).listMergeRequestDiscussions("group/repo", "!42", new GitlabPageRequest(5, 40));
        verify(responseWriter).write(discussions);
    }

    @Test
    void getMergeRequestPipelinesMapsProjectIidAndPagination() {
        List<Pipeline> pipelines = List.of();
        when(gateway.listMergeRequestPipelines("group/repo", "!42", new GitlabPageRequest(6, 60)))
                .thenReturn(pipelines);

        String response = mergeRequestTools.getMergeRequestPipelines("group/repo", "!42", 6, 60);

        assertThat(response).isEqualTo("json");
        verify(gateway).listMergeRequestPipelines("group/repo", "!42", new GitlabPageRequest(6, 60));
        verify(responseWriter).write(pipelines);
    }

    @Test
    void getPipelineMapsProjectAndPipelineIdToEndpoint() {
        Pipeline pipeline = pipeline(123L);
        when(gateway.getPipeline("group/repo", "pipeline-url")).thenReturn(pipeline);

        String response = pipelineTools.getPipeline("group/repo", "pipeline-url");

        assertThat(response).isEqualTo("json");
        verify(gateway).getPipeline("group/repo", "pipeline-url");
        verify(responseWriter).write(pipeline);
    }

    @Test
    void listPipelineJobsMapsProjectPipelineAndPagination() {
        List<Job> jobs = List.of(job(8L, "test"));
        when(gateway.listPipelineJobs("group/repo", "pipeline-url", true, new GitlabPageRequest(2, 30)))
                .thenReturn(jobs);

        String response = pipelineTools.listPipelineJobs("group/repo", "pipeline-url", true, 2, 30);

        assertThat(response).isEqualTo("json");
        verify(gateway).listPipelineJobs("group/repo", "pipeline-url", true, new GitlabPageRequest(2, 30));
        verify(responseWriter).write(jobs);
    }

    @Test
    void getJobTraceMapsProjectJobAndDefaultLimitToTraceEndpoint() {
        when(gateway.getJobTraceTail("group/repo", "job-url", 60_000)).thenReturn("trace");

        String response = jobTools.getJobTrace("group/repo", "job-url", null);

        assertThat(response).isEqualTo("trace");
        verify(gateway).getJobTraceTail("group/repo", "job-url", 60_000);
    }

    @Test
    void getJobTraceTailMapsProjectJobAndExplicitLimitToTraceEndpoint() {
        when(gateway.getJobTraceTail("group/repo", "job-url", 4096)).thenReturn("trace tail");

        String response = jobTools.getJobTraceTail("group/repo", "job-url", 4096);

        assertThat(response).isEqualTo("trace tail");
        verify(gateway).getJobTraceTail("group/repo", "job-url", 4096);
    }

    @Test
    void listJobArtifactsReadsArchiveAndAppliesFiltersAndPagination() {
        List<ArtifactFile> artifacts =
                List.of(new ArtifactFile("junit.xml", "target/junit.xml", "file", 42L, "100644"));
        when(gateway.listJobArtifacts("group/repo", "job-url", "target", true, new GitlabPageRequest(3, 40)))
                .thenReturn(artifacts);

        String response = jobTools.listJobArtifacts("group/repo", "job-url", "target", true, 3, 40);

        assertThat(response).isEqualTo("json");
        verify(gateway).listJobArtifacts("group/repo", "job-url", "target", true, new GitlabPageRequest(3, 40));
        verify(responseWriter).write(artifacts);
    }

    @Test
    void findJobArtifactFilesReadsArchiveAndAppliesPattern() {
        List<ArtifactFile> artifacts = List.of(new ArtifactFile("TEST.xml", "target/TEST.xml", "file", 42L, null));
        when(gateway.findJobArtifactFiles(
                        "group/repo", "job-url", "**/TEST-*.xml", false, new GitlabPageRequest(1, 20)))
                .thenReturn(artifacts);

        String response = jobTools.findJobArtifactFiles("group/repo", "job-url", "**/TEST-*.xml", false, 1, 20);

        assertThat(response).isEqualTo("json");
        verify(gateway)
                .findJobArtifactFiles("group/repo", "job-url", "**/TEST-*.xml", false, new GitlabPageRequest(1, 20));
        verify(responseWriter).write(artifacts);
    }

    @Test
    void getJobArtifactFileMapsProjectJobArtifactPathAndLimit() {
        when(gateway.getJobArtifactFile("group/repo", "job-url", "/target/surefire-reports/TEST.xml", 2048))
                .thenReturn("artifact");

        String response =
                jobTools.getJobArtifactFile("group/repo", "job-url", "/target/surefire-reports/TEST.xml", 2048);

        assertThat(response).isEqualTo("artifact");
        verify(gateway).getJobArtifactFile("group/repo", "job-url", "/target/surefire-reports/TEST.xml", 2048);
    }

    @Test
    void analyzeFailedPipelineDelegatesToDiagnosticsServiceAndSerializesResult() {
        PipelineDiagnosticsResult result = new PipelineDiagnosticsResult(
                pipeline(123L), "summary", List.of(), List.of(), true, false, false, null);
        diagnosticsService.result = result;

        String response = diagnosticsTools.analyzeFailedPipeline(
                "group/repo", "pipeline-url", null, true, 4096, false, false, false);

        assertThat(response).isEqualTo("json");
        assertThat(diagnosticsService.lastCall)
                .isEqualTo(new DiagnosticCall("group/repo", "pipeline-url", null, true, 4096, false, false, false));
        verify(responseWriter).write(result);
    }

    private record DiagnosticCall(
            String projectId,
            String pipelineId,
            String mergeRequestIid,
            Boolean includeTraces,
            Integer maxTraceBytesPerJob,
            Boolean includeRawTraces,
            Boolean includeArtifactHints,
            Boolean includeDetails) {}

    private static final class RecordingPipelineDiagnosticsService extends PipelineDiagnosticsService {

        private PipelineDiagnosticsResult result;
        private DiagnosticCall lastCall;

        private RecordingPipelineDiagnosticsService(GitlabGateway gitlab) {
            super(
                    gitlab,
                    mock(PipelineContextCollector.class),
                    mock(PipelineAnalysisEngine.class),
                    new TraceAnalyzer(),
                    new MavenFailureAnalyzer(),
                    new SurefireReportAnalyzer(),
                    new LogMatcher(),
                    new ArtifactHintDetector());
        }

        @Override
        public PipelineDiagnosticsResult analyze(
                String projectId,
                String pipelineId,
                String mergeRequestIid,
                Boolean includeTraces,
                Integer maxTraceBytesPerJob,
                Boolean includeRawTraces,
                Boolean includeArtifactHints) {
            return analyze(
                    projectId,
                    pipelineId,
                    mergeRequestIid,
                    includeTraces,
                    maxTraceBytesPerJob,
                    includeRawTraces,
                    includeArtifactHints,
                    false);
        }

        @Override
        public PipelineDiagnosticsResult analyze(
                String projectId,
                String pipelineId,
                String mergeRequestIid,
                Boolean includeTraces,
                Integer maxTraceBytesPerJob,
                Boolean includeRawTraces,
                Boolean includeArtifactHints,
                Boolean includeDetails) {
            lastCall = new DiagnosticCall(
                    projectId,
                    pipelineId,
                    mergeRequestIid,
                    includeTraces,
                    maxTraceBytesPerJob,
                    includeRawTraces,
                    includeArtifactHints,
                    includeDetails);
            return result;
        }
    }
}
