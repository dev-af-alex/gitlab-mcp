package com.alexaf.gitlabmcp.tool.gitlab;

import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitlabToolComponentsTest {

    private RecordingGitlabApiClient gitlab;
    private RecordingPipelineDiagnosticsService diagnosticsService;
    private GitlabProjectTools projectTools;
    private GitlabMergeRequestTools mergeRequestTools;
    private GitlabPipelineTools pipelineTools;
    private GitlabJobTools jobTools;
    private GitlabDiagnosticsTools diagnosticsTools;

    private static Project project(Long id, String name) {
        return new Project(id, null, name, null, null, "group/" + name, "main",
                null, null, null, null, "private", false, null, null);
    }

    private static Pipeline pipeline(Long id) {
        return new Pipeline(id, 1L, 11L, "abc123", "main", "failed", "push",
                null, null, null, null, 60L, 1L, "https://gitlab.example/pipelines/" + id);
    }

    private static Job job(Long id, String name) {
        return new Job(id, name, "test", "failed", "script_failure", null, "main",
                false, false, null, null, null, 60.0, 1.0, List.of());
    }

    private static MergeRequest mergeRequest(Long iid) {
        return new MergeRequest(1L, iid, 11L, "MR", null, "opened", null, null,
                "main", "feature", null, List.of(), List.of(), List.of(), false, false,
                0, null, null, null, null, null, null, false, true, null, null, null);
    }

    @BeforeEach
    void setUp() {
        gitlab = new RecordingGitlabApiClient();
        diagnosticsService = new RecordingPipelineDiagnosticsService(gitlab);
        projectTools = new GitlabProjectTools(gitlab);
        mergeRequestTools = new GitlabMergeRequestTools(gitlab);
        pipelineTools = new GitlabPipelineTools(gitlab);
        jobTools = new GitlabJobTools(gitlab);
        diagnosticsTools = new GitlabDiagnosticsTools(gitlab, diagnosticsService);
    }

    @Test
    void getCurrentUserCallsUserEndpointAndSerializesResponse() {
        CurrentUser user = new CurrentUser(7L, "alice", "Alice", "active", null, null, null, null, null);
        gitlab.objectResponse = user;

        String response = projectTools.getCurrentUser();

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.lastCall).isEqualTo(new Call("/user", CurrentUser.class, List.of()));
        assertThat(gitlab.jsonInput).isSameAs(user);
    }

    @Test
    void searchProjectsMapsPaginationAndSearchToProjectsEndpoint() {
        List<Project> projects = List.of(project(11L, "demo"));
        gitlab.listResponse = projects;

        String response = projectTools.searchProjects("demo", 2, 50);

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.lastCall).isEqualTo(new Call("/projects", Project.class, List.of(
                new GitlabApiClient.QueryParam("search", "demo"),
                new GitlabApiClient.QueryParam("membership", true),
                new GitlabApiClient.QueryParam("page", 2),
                new GitlabApiClient.QueryParam("per_page", 50)
        )));
        assertThat(gitlab.jsonInput).isSameAs(projects);
    }

    @Test
    void getProjectEncodesProjectIdBeforeCallingProjectEndpoint() {
        Project project = project(11L, "repo");
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.objectResponse = project;

        String response = projectTools.getProject("group/repo");

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.projectIdInput).isEqualTo("group/repo");
        assertThat(gitlab.lastCall).isEqualTo(new Call("/projects/group%2Frepo", Project.class, List.of()));
        assertThat(gitlab.jsonInput).isSameAs(project);
    }

    @Test
    void listMergeRequestsMapsFiltersSortingAndPagination() {
        List<MergeRequest> mergeRequests = List.of();
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.listResponse = mergeRequests;

        String response = mergeRequestTools.listMergeRequests(
                "group/repo", "open", "bug", "feature", "main", "alice", "bob", 3, 25);

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.projectIdInput).isEqualTo("group/repo");
        assertThat(gitlab.stateInput).isEqualTo("open");
        assertThat(gitlab.lastCall).isEqualTo(new Call("/projects/group%2Frepo/merge_requests", MergeRequest.class, List.of(
                new GitlabApiClient.QueryParam("state", "opened"),
                new GitlabApiClient.QueryParam("search", "bug"),
                new GitlabApiClient.QueryParam("source_branch", "feature"),
                new GitlabApiClient.QueryParam("target_branch", "main"),
                new GitlabApiClient.QueryParam("author_username", "alice"),
                new GitlabApiClient.QueryParam("reviewer_username", "bob"),
                new GitlabApiClient.QueryParam("order_by", "updated_at"),
                new GitlabApiClient.QueryParam("sort", "desc"),
                new GitlabApiClient.QueryParam("page", 3),
                new GitlabApiClient.QueryParam("per_page", 25)
        )));
        assertThat(gitlab.jsonInput).isSameAs(mergeRequests);
    }

    @Test
    void getMergeRequestMapsProjectAndIidToEndpoint() {
        MergeRequest mergeRequest = mergeRequest(42L);
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.mergeRequestIidReturn = 42L;
        gitlab.objectResponse = mergeRequest;

        String response = mergeRequestTools.getMergeRequest("group/repo", "!42");

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.projectIdInput).isEqualTo("group/repo");
        assertThat(gitlab.mergeRequestIidInput).isEqualTo("!42");
        assertThat(gitlab.lastCall).isEqualTo(new Call("/projects/group%2Frepo/merge_requests/42", MergeRequest.class, List.of()));
        assertThat(gitlab.jsonInput).isSameAs(mergeRequest);
    }

    @Test
    void getMergeRequestChangesMapsProjectAndIidToChangesEndpoint() {
        MergeRequestChanges changes = new MergeRequestChanges(1L, 42L, 11L, "MR", null, "opened",
                "main", "feature", null, null, List.of());
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.mergeRequestIidReturn = 42L;
        gitlab.objectResponse = changes;

        String response = mergeRequestTools.getMergeRequestChanges("group/repo", "!42");

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.lastCall).isEqualTo(new Call("/projects/group%2Frepo/merge_requests/42/changes",
                MergeRequestChanges.class, List.of()));
        assertThat(gitlab.jsonInput).isSameAs(changes);
    }

    @Test
    void getMergeRequestCommitsMapsProjectIidAndPagination() {
        List<Commit> commits = List.of();
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.mergeRequestIidReturn = 42L;
        gitlab.listResponse = commits;

        String response = mergeRequestTools.getMergeRequestCommits("group/repo", "!42", 4, 30);

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.lastCall).isEqualTo(new Call("/projects/group%2Frepo/merge_requests/42/commits",
                Commit.class, List.of(
                new GitlabApiClient.QueryParam("page", 4),
                new GitlabApiClient.QueryParam("per_page", 30)
        )));
        assertThat(gitlab.jsonInput).isSameAs(commits);
    }

    @Test
    void getMergeRequestDiscussionsMapsProjectIidAndPagination() {
        List<Discussion> discussions = List.of();
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.mergeRequestIidReturn = 42L;
        gitlab.listResponse = discussions;

        String response = mergeRequestTools.getMergeRequestDiscussions("group/repo", "!42", 5, 40);

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.lastCall).isEqualTo(new Call("/projects/group%2Frepo/merge_requests/42/discussions",
                Discussion.class, List.of(
                new GitlabApiClient.QueryParam("page", 5),
                new GitlabApiClient.QueryParam("per_page", 40)
        )));
        assertThat(gitlab.jsonInput).isSameAs(discussions);
    }

    @Test
    void getMergeRequestPipelinesMapsProjectIidAndPagination() {
        List<Pipeline> pipelines = List.of();
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.mergeRequestIidReturn = 42L;
        gitlab.listResponse = pipelines;

        String response = mergeRequestTools.getMergeRequestPipelines("group/repo", "!42", 6, 60);

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.lastCall).isEqualTo(new Call("/projects/group%2Frepo/merge_requests/42/pipelines",
                Pipeline.class, List.of(
                new GitlabApiClient.QueryParam("page", 6),
                new GitlabApiClient.QueryParam("per_page", 60)
        )));
        assertThat(gitlab.jsonInput).isSameAs(pipelines);
    }

    @Test
    void getPipelineMapsProjectAndPipelineIdToEndpoint() {
        Pipeline pipeline = pipeline(123L);
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.objectResponse = pipeline;

        String response = pipelineTools.getPipeline("group/repo", "pipeline-url");

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.projectIdInput).isEqualTo("group/repo");
        assertThat(gitlab.pipelineIdInput).isEqualTo("pipeline-url");
        assertThat(gitlab.lastCall).isEqualTo(new Call("/projects/group%2Frepo/pipelines/123", Pipeline.class, List.of()));
        assertThat(gitlab.jsonInput).isSameAs(pipeline);
    }

    @Test
    void listPipelineJobsMapsProjectPipelineAndPagination() {
        List<Job> jobs = List.of(job(8L, "test"));
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.listResponse = jobs;

        String response = pipelineTools.listPipelineJobs("group/repo", "pipeline-url", true, 2, 30);

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.pipelineIdInput).isEqualTo("pipeline-url");
        assertThat(gitlab.lastCall).isEqualTo(new Call("/projects/group%2Frepo/pipelines/123/jobs",
                Job.class, List.of(
                new GitlabApiClient.QueryParam("include_retried", true),
                new GitlabApiClient.QueryParam("page", 2),
                new GitlabApiClient.QueryParam("per_page", 30)
        )));
        assertThat(gitlab.jsonInput).isSameAs(jobs);
    }

    @Test
    void getJobTraceMapsProjectJobAndDefaultLimitToTraceEndpoint() {
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.textResponse = "trace";

        String response = jobTools.getJobTrace("group/repo", "job-url", null);

        assertThat(response).isEqualTo("trace");
        assertThat(gitlab.jobIdInput).isEqualTo("job-url");
        assertThat(gitlab.lastTailCall).isEqualTo(new TextCall("/projects/group%2Frepo/jobs/8/trace", 60_000));
    }

    @Test
    void getJobTraceTailMapsProjectJobAndExplicitLimitToTraceEndpoint() {
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.textResponse = "trace tail";

        String response = jobTools.getJobTraceTail("group/repo", "job-url", 4096);

        assertThat(response).isEqualTo("trace tail");
        assertThat(gitlab.jobIdInput).isEqualTo("job-url");
        assertThat(gitlab.lastTailCall).isEqualTo(new TextCall("/projects/group%2Frepo/jobs/8/trace", 4096));
    }

    @Test
    void listJobArtifactsReadsArchiveAndAppliesFiltersAndPagination() {
        List<ArtifactFile> artifacts = List.of(new ArtifactFile("junit.xml", "target/junit.xml", "file", 42L, "100644"));
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.artifactFilesResponse = artifacts;

        String response = jobTools.listJobArtifacts("group/repo", "job-url", "target", true, 3, 40);

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.jobIdInput).isEqualTo("job-url");
        assertThat(gitlab.lastArtifactArchiveCall).isEqualTo(new ArtifactArchiveCall(
                "/projects/group%2Frepo/jobs/8/artifacts", "target", true, 3, 40));
        assertThat(gitlab.jsonInput).isSameAs(artifacts);
    }

    @Test
    void findJobArtifactFilesReadsArchiveAndAppliesPattern() {
        List<ArtifactFile> artifacts = List.of(new ArtifactFile("TEST.xml", "target/TEST.xml", "file", 42L, null));
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.artifactFilesResponse = artifacts;

        String response = jobTools.findJobArtifactFiles("group/repo", "job-url", "**/TEST-*.xml", false, 1, 20);

        assertThat(response).isEqualTo("json");
        assertThat(gitlab.lastFindArtifactCall).isEqualTo(new FindArtifactCall(
                "/projects/group%2Frepo/jobs/8/artifacts", "**/TEST-*.xml", false, 1, 20));
        assertThat(gitlab.jsonInput).isSameAs(artifacts);
    }

    @Test
    void getJobArtifactFileMapsProjectJobArtifactPathAndLimit() {
        gitlab.projectPathReturn = "group%2Frepo";
        gitlab.textResponse = "artifact";

        String response = jobTools.getJobArtifactFile("group/repo", "job-url", "/target/surefire-reports/TEST.xml", 2048);

        assertThat(response).isEqualTo("artifact");
        assertThat(gitlab.jobIdInput).isEqualTo("job-url");
        assertThat(gitlab.lastTextCall).isEqualTo(new TextCall(
                "/projects/group%2Frepo/jobs/8/artifacts/target/surefire-reports/TEST.xml", 2048));
    }

    @Test
    void analyzeFailedPipelineDelegatesToDiagnosticsServiceAndSerializesResult() {
        PipelineDiagnosticsResult result = new PipelineDiagnosticsResult(
                pipeline(123L),
                "summary",
                List.of(),
                List.of(),
                true,
                false,
                false,
                null);
        diagnosticsService.result = result;

        String response = diagnosticsTools.analyzeFailedPipeline("group/repo", "pipeline-url", null, true, 4096, false, false, false);

        assertThat(response).isEqualTo("json");
        assertThat(diagnosticsService.lastCall).isEqualTo(new DiagnosticCall(
                "group/repo", "pipeline-url", null, true, 4096, false, false, false));
        assertThat(gitlab.jsonInput).isSameAs(result);
    }

    private record Call(String path, Class<?> type, List<GitlabApiClient.QueryParam> params) {
    }

    private record TextCall(String path, Integer maxBytes) {
    }

    private record DiagnosticCall(
            String projectId,
            String pipelineId,
            String mergeRequestIid,
            Boolean includeTraces,
            Integer maxTraceBytesPerJob,
            Boolean includeRawTraces,
            Boolean includeArtifactHints,
            Boolean includeDetails
    ) {
    }

    private record ArtifactArchiveCall(String path, String artifactPath, Boolean recursive, Integer page,
                                       Integer perPage) {
    }

    private record FindArtifactCall(String path, String pattern, Boolean regex, Integer page, Integer perPage) {
    }

    private static final class RecordingPipelineDiagnosticsService extends PipelineDiagnosticsService {

        private PipelineDiagnosticsResult result;
        private DiagnosticCall lastCall;

        private RecordingPipelineDiagnosticsService(GitlabApiClient gitlab) {
            super(gitlab, new TraceAnalyzer(), new MavenFailureAnalyzer(), new SurefireReportAnalyzer(),
                    new LogMatcher(), new ArtifactHintDetector());
        }

        @Override
        public PipelineDiagnosticsResult analyze(String projectId, String pipelineId, String mergeRequestIid,
                                                 Boolean includeTraces, Integer maxTraceBytesPerJob, Boolean includeRawTraces, Boolean includeArtifactHints) {
            return analyze(projectId, pipelineId, mergeRequestIid, includeTraces, maxTraceBytesPerJob,
                    includeRawTraces, includeArtifactHints, false);
        }

        @Override
        public PipelineDiagnosticsResult analyze(String projectId, String pipelineId, String mergeRequestIid,
                                                 Boolean includeTraces, Integer maxTraceBytesPerJob, Boolean includeRawTraces,
                                                 Boolean includeArtifactHints, Boolean includeDetails) {
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

    private static final class RecordingGitlabApiClient extends GitlabApiClient {

        private Object objectResponse;
        private List<?> listResponse = List.of();
        private String textResponse = "";
        private List<ArtifactFile> artifactFilesResponse = List.of();
        private Object jsonInput;
        private Call lastCall;
        private TextCall lastTextCall;
        private TextCall lastTailCall;
        private ArtifactArchiveCall lastArtifactArchiveCall;
        private FindArtifactCall lastFindArtifactCall;
        private String projectPathReturn = "project";
        private String projectIdInput;
        private long mergeRequestIidReturn = 1L;
        private String mergeRequestIidInput;
        private long pipelineIdReturn = 123L;
        private String pipelineIdInput;
        private long jobIdReturn = 8L;
        private String jobIdInput;
        private String stateInput;

        private RecordingGitlabApiClient() {
            super(new GitlabProperties("https://gitlab.example", "token", List.of(), 20, 100),
                    new ObjectMapper(), RestClient.builder());
        }

        @Override
        public <T> T getObject(String path, Class<T> type, QueryParam... queryParams) {
            lastCall = new Call(path, type, List.of(queryParams));
            return type.cast(objectResponse);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> List<T> getList(String path, Class<T> itemType, QueryParam... queryParams) {
            lastCall = new Call(path, itemType, List.of(queryParams));
            return (List<T>) listResponse;
        }

        @Override
        public String getLimitedText(String path, Integer maxBytes, QueryParam... queryParams) {
            lastTextCall = new TextCall(path, maxBytes);
            return textResponse;
        }

        @Override
        public String getTailText(String path, Integer maxBytes, QueryParam... queryParams) {
            lastTailCall = new TextCall(path, maxBytes);
            return textResponse;
        }

        @Override
        public List<ArtifactFile> listArtifactArchive(String archivePath, String path, Boolean recursive, Integer page, Integer perPage) {
            lastArtifactArchiveCall = new ArtifactArchiveCall(archivePath, path, recursive, page, perPage);
            return artifactFilesResponse;
        }

        @Override
        public List<ArtifactFile> findArtifactArchiveFiles(String archivePath, String pattern, Boolean regex, Integer page, Integer perPage) {
            lastFindArtifactCall = new FindArtifactCall(archivePath, pattern, regex, page, perPage);
            return artifactFilesResponse;
        }

        @Override
        public String json(Object value) {
            jsonInput = value;
            return "json";
        }

        @Override
        public String projectPath(String projectId) {
            projectIdInput = projectId;
            return projectPathReturn;
        }

        @Override
        public long mergeRequestIid(String value) {
            mergeRequestIidInput = value;
            return mergeRequestIidReturn;
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
        public String mergeRequestState(String state) {
            stateInput = state;
            return "open".equals(state) ? "opened" : state;
        }

        @Override
        public int page(Integer page) {
            return page == null ? 1 : page;
        }

        @Override
        public int perPage(Integer perPage) {
            return perPage == null ? 20 : perPage;
        }
    }
}
