package com.alexaf.gitlabmcp.gitlab.diagnostics;

import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.JobArtifact;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineDiagnosticsServiceTest {

    private RecordingGitlabApiClient gitlab;
    private PipelineDiagnosticsService service;

    private static Pipeline pipeline(Long id, String status) {
        return new Pipeline(id, 1L, 11L, "abc123", "main", status, "push",
                null, null, null, null, 60L, 1L, "https://gitlab.example/pipelines/" + id);
    }

    private static Job job(Long id, String name, String status, String failureReason) {
        return new Job(id, name, "test", status, failureReason, "https://gitlab.example/jobs/" + id,
                "main", false, false, null, null, null, 60.0, 1.0,
                List.of(new JobArtifact("junit", 1234L, "junit.xml", "xml")));
    }

    @BeforeEach
    void setUp() {
        gitlab = new RecordingGitlabApiClient();
        service = new PipelineDiagnosticsService(gitlab, new TraceAnalyzer(), new MavenFailureAnalyzer(),
                new SurefireReportAnalyzer(), new LogMatcher(), new ArtifactHintDetector());
    }

    @Test
    void analyzeByPipelineIdCollectsFailedJobsTracesAndArtifactHints() {
        gitlab.pipelineIdReturn = 123L;
        gitlab.objectResponses.put("/projects/group%2Frepo/pipelines/123", pipeline(123L, "failed"));
        gitlab.listResponses.put("/projects/group%2Frepo/pipelines/123/jobs", List.of(
                job(8L, "test", "failed", "script_failure"),
                job(9L, "deploy", "canceled", "user_blocked"),
                job(10L, "build", "success", null)));
        gitlab.textResponses.put("/projects/group%2Frepo/jobs/8/trace",
                "mvn test\nTests run: 12, Failures: 1\nBUILD FAILURE");
        gitlab.artifactFiles.put("/projects/group%2Frepo/jobs/8/artifacts", List.of(
                new ArtifactFile("TEST-ServiceTest.xml", "target/surefire-reports/TEST-ServiceTest.xml", "file", 123L, "100644"),
                new ArtifactFile("app.jar", "target/app.jar", "file", 1024L, "100644")));

        PipelineDiagnosticsResult result = service.analyze("group/repo", "pipeline-url", null, true, 4096, true, true);

        assertThat(gitlab.projectIdInput).isEqualTo("group/repo");
        assertThat(gitlab.pipelineIdInput).isEqualTo("pipeline-url");
        assertThat(result.pipeline().id()).isEqualTo(123L);
        assertThat(result.summary()).contains("Pipeline 123 failed in 1 job(s)");
        assertThat(result.failedJobs()).hasSize(1);
        JobDiagnostic failedJob = result.failedJobs().getFirst();
        assertThat(failedJob.id()).isEqualTo(8L);
        assertThat(failedJob.detectedCause()).isEqualTo("Maven/JUnit test failure");
        assertThat(failedJob.evidence()).contains("failure_reason: script_failure", "BUILD FAILURE");
        assertThat(failedJob.failureSummary().maven().testFailureDetected()).isTrue();
        assertThat(failedJob.trace()).contains("Tests run: 12");
        assertThat(failedJob.usefulArtifacts())
                .contains("junit junit.xml xml", "target/surefire-reports/TEST-ServiceTest.xml")
                .doesNotContain("target/app.jar");
        assertThat(result.otherNotSuccessfulJobs()).extracting(JobSummary::name).containsExactly("deploy");
        assertThat(result.detailsIncluded()).isFalse();
        assertThat(failedJob.failureSummary().importantTraceMatches().matches())
                .allSatisfy(match -> {
                    assertThat(match.before()).isEmpty();
                    assertThat(match.after()).isEmpty();
                });
        assertThat(gitlab.tailCalls).containsExactly("/projects/group%2Frepo/jobs/8/trace:4096");
    }

    @Test
    void analyzeByMergeRequestChoosesLatestFailedPipelineAndCanSkipTraceAndArtifacts() {
        gitlab.mergeRequestIidReturn = 42L;
        gitlab.listResponses.put("/projects/group%2Frepo/merge_requests/42/pipelines", List.of(
                pipeline(124L, "success"),
                pipeline(123L, "failed")));
        gitlab.listResponses.put("/projects/group%2Frepo/pipelines/123/jobs", List.of(job(8L, "test", "failed", null)));

        PipelineDiagnosticsResult result = service.analyze("group/repo", null, "!42", false, null, false, false);

        assertThat(gitlab.mergeRequestIidInput).isEqualTo("!42");
        assertThat(result.pipeline().id()).isEqualTo(123L);
        assertThat(result.tracesIncluded()).isFalse();
        assertThat(result.rawTracesIncluded()).isFalse();
        assertThat(result.artifactHintsIncluded()).isFalse();
        assertThat(result.failedJobs()).singleElement()
                .satisfies(job -> {
                    assertThat(job.trace()).isNull();
                    assertThat(job.usefulArtifacts()).isEmpty();
                });
        assertThat(gitlab.tailCalls).isEmpty();
    }

    @Test
    void analyzeReadsSurefireTxtForMavenErrorAndClassifiesInfrastructureRootCause() {
        gitlab.pipelineIdReturn = 123L;
        gitlab.objectResponses.put("/projects/group%2Frepo/pipelines/123", pipeline(123L, "failed"));
        gitlab.listResponses.put("/projects/group%2Frepo/pipelines/123/jobs", List.of(job(8L, "tests", "failed", "script_failure")));
        gitlab.textResponses.put("/projects/group%2Frepo/jobs/8/trace", """
                [ERROR] Errors:
                [ERROR]   OrderControllerTest » IllegalState Failed to load Applic...
                [ERROR] Tests run: 2600, Failures: 0, Errors: 3, Skipped: 11
                """);
        gitlab.artifactFiles.put("/projects/group%2Frepo/jobs/8/artifacts:.*\\QOrderControllerTest\\E\\.txt$",
                List.of(new ArtifactFile(
                        "OrderControllerTest.txt",
                        "target/surefire-reports/OrderControllerTest.txt",
                        "file",
                        123L,
                        "100644")));
        gitlab.textResponses.put("/projects/group%2Frepo/jobs/8/artifacts/target/surefire-reports/OrderControllerTest.txt",
                """
                        Test set: com.example.OrderControllerTest
                        Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
                        java.lang.IllegalStateException: Failed to load ApplicationContext
                        Caused by: org.testcontainers.containers.ContainerLaunchException: Container startup failed for image registry.example.com/ryuk:0.6.0
                        Status 500: {"message":"E: oscap: dpkginfo_init has failed. sh: bash: not found"}
                        """);

        PipelineDiagnosticsResult result = service.analyze("group/repo", "pipeline-url", null, true, 4096, false, true);

        JobFailureSummary summary = result.failedJobs().getFirst().failureSummary();
        assertThat(summary.maven().errorTests()).singleElement()
                .satisfies(error -> {
                    assertThat(error.className()).isEqualTo("OrderControllerTest");
                    assertThat(error.errorType()).isEqualTo("IllegalState");
                });
        assertThat(summary.surefireReports()).singleElement()
                .satisfies(report -> {
                    assertThat(report.rootCauseType()).isEqualTo("testcontainers_container_startup");
                    assertThat(report.infrastructure()).isTrue();
                    assertThat(report.rootCauseMessage()).contains("Container startup failed");
                });
        assertThat(summary.primaryCause().infrastructure()).isTrue();
        assertThat(summary.primaryCause().recommendation()).isEqualTo("retry_pipeline_or_fix_ci_runner");
    }

    @Test
    void analyzeUsesMavenLogArtifactToSelectOnlyFailedSurefireClasses() {
        gitlab.pipelineIdReturn = 123L;
        gitlab.objectResponses.put("/projects/group%2Frepo/pipelines/123", pipeline(123L, "failed"));
        gitlab.listResponses.put("/projects/group%2Frepo/pipelines/123/jobs",
                List.of(job(8L, "tests", "failed", "script_failure")));
        gitlab.textResponses.put("/projects/group%2Frepo/jobs/8/trace", "mvn test\n[INFO] BUILD FAILURE");

        String archive = "/projects/group%2Frepo/jobs/8/artifacts";
        gitlab.artifactFiles.put(archive + ":.*\\.log$", List.of(new ArtifactFile(
                "build.log", "build.log", "file", 200L, "100644")));
        gitlab.textResponses.put(archive + "/build.log", """
                [ERROR] Failures:
                [ERROR]   CatalogControllerTest.getItems:227 expected: <true> but was: <false>
                [ERROR] Tests run: 10, Failures: 1, Errors: 0, Skipped: 0
                [INFO] BUILD FAILURE
                """);
        gitlab.artifactFiles.put(archive + ":.*\\QCatalogControllerTest\\E\\.txt$", List.of(
                new ArtifactFile("CatalogControllerTest.txt",
                        "target/surefire-reports/CatalogControllerTest.txt", "file", 100L, "100644")));
        gitlab.textResponses.put(archive + "/target/surefire-reports/CatalogControllerTest.txt", """
                Test set: com.example.CatalogControllerTest
                Tests run: 10, Failures: 1, Errors: 0, Skipped: 0
                getItems  Time elapsed: 0.1 s  <<< FAILURE!
                org.opentest4j.AssertionFailedError: expected: <true> but was: <false>
                at com.example.CatalogControllerTest.getItems(CatalogControllerTest.java:227)
                """);

        PipelineDiagnosticsResult result = service.analyze("group/repo", "pipeline-url", null,
                true, 4096, false, false);

        JobFailureSummary summary = result.failedJobs().getFirst().failureSummary();
        assertThat(summary.maven().failingTests()).singleElement()
                .satisfies(failure -> assertThat(failure.className()).isEqualTo("CatalogControllerTest"));
        assertThat(summary.surefireReports()).singleElement()
                .satisfies(report -> assertThat(report.testFailures()).singleElement()
                        .satisfies(failure -> assertThat(failure.sourceLocation()).endsWith(":227")));
    }

    @Test
    void compactSummaryGroupsApplicationContextCascades() {
        gitlab.pipelineIdReturn = 123L;
        gitlab.objectResponses.put("/projects/group%2Frepo/pipelines/123", pipeline(123L, "failed"));
        gitlab.listResponses.put("/projects/group%2Frepo/pipelines/123/jobs",
                List.of(job(8L, "tests", "failed", "script_failure")));
        gitlab.textResponses.put("/projects/group%2Frepo/jobs/8/trace", """
                [ERROR] Errors:
                [ERROR]   FirstContextTest » IllegalState ApplicationContext failure threshold (1) exceeded
                [ERROR]   SecondContextTest » IllegalState ApplicationContext failure threshold (1) exceeded
                [ERROR] Tests run: 20, Failures: 0, Errors: 2, Skipped: 0
                [INFO] BUILD FAILURE
                """);

        PipelineDiagnosticsResult result = service.analyze("group/repo", "pipeline-url", null,
                true, 4096, false, false);

        JobFailureSummary summary = result.failedJobs().getFirst().failureSummary();
        assertThat(summary.maven().errorTests()).isEmpty();
        assertThat(summary.contextCascadeClasses())
                .containsExactly("FirstContextTest", "SecondContextTest");
        assertThat(result.detailsIncluded()).isFalse();
    }

    @Test
    void analyzeRequiresPipelineIdOrMergeRequestIid() {
        assertThatThrownBy(() -> service.analyze("group/repo", null, null, true, null, false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Either pipelineId or mergeRequestIid must be set");
    }

    private static final class RecordingGitlabApiClient extends GitlabApiClient {

        private final Map<String, Object> objectResponses = new HashMap<>();
        private final Map<String, List<?>> listResponses = new HashMap<>();
        private final Map<String, List<ArtifactFile>> artifactFiles = new HashMap<>();
        private final Map<String, String> textResponses = new HashMap<>();
        private final List<String> tailCalls = new ArrayList<>();
        private String projectIdInput;
        private long pipelineIdReturn = 1L;
        private String pipelineIdInput;
        private long mergeRequestIidReturn = 1L;
        private String mergeRequestIidInput;

        private RecordingGitlabApiClient() {
            super(new GitlabProperties("https://gitlab.example", "token", List.of(), 20, 100),
                    new ObjectMapper(), RestClient.builder());
        }

        @Override
        public String projectPath(String projectId) {
            projectIdInput = projectId;
            return "group%2Frepo";
        }

        @Override
        public long pipelineId(String value) {
            pipelineIdInput = value;
            return pipelineIdReturn;
        }

        @Override
        public long mergeRequestIid(String value) {
            mergeRequestIidInput = value;
            return mergeRequestIidReturn;
        }

        @Override
        public <T> T getObject(String path, Class<T> type, QueryParam... queryParams) {
            return type.cast(objectResponses.get(path));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> List<T> getList(String path, Class<T> itemType, QueryParam... queryParams) {
            return (List<T>) listResponses.getOrDefault(path, List.of());
        }

        @Override
        public String getTailText(String path, Integer maxBytes, QueryParam... queryParams) {
            tailCalls.add(path + ":" + maxBytes);
            return textResponses.get(path);
        }

        @Override
        public List<ArtifactFile> listArtifactArchive(String archivePath, String path, Boolean recursive, Integer page, Integer perPage) {
            return artifactFiles.getOrDefault(archivePath, List.of());
        }

        @Override
        public List<ArtifactFile> findArtifactArchiveFiles(String archivePath, String pattern, Boolean regex, Integer page, Integer perPage) {
            return artifactFiles.getOrDefault(archivePath + ":" + pattern, List.of());
        }

        @Override
        public String getLimitedText(String path, Integer maxBytes, QueryParam... queryParams) {
            return textResponses.get(path);
        }
    }
}
