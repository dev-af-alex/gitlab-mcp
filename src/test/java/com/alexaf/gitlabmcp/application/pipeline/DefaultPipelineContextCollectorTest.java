package com.alexaf.gitlabmcp.application.pipeline;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.alexaf.gitlabmcp.domain.GitlabPage;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.domain.PipelineCollectionOptions;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabDownloadLimitException;
import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;
import com.alexaf.gitlabmcp.gitlab.dto.GitlabTestReport;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.alexaf.gitlabmcp.gitlab.dto.PipelineBridge;
import com.alexaf.gitlabmcp.port.GitlabGateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultPipelineContextCollectorTest {

    private final GitlabGateway gitlab = mock(GitlabGateway.class);
    private final DefaultPipelineContextCollector collector = new DefaultPipelineContextCollector(gitlab, 2);

    @Test
    void collectsAllJobsUpToConfiguredLimitAndPreservesTruncation() {
        Pipeline pipeline = pipeline(42L, "failed");
        List<Job> jobs = List.of(job(1L), job(2L));
        when(gitlab.getPipeline("group/repo", "pipeline-url")).thenReturn(pipeline);
        when(gitlab.getPipelineJobs("group/repo", "pipeline-url", false, 2))
                .thenReturn(new GitlabPage<>(jobs, "https://gitlab.example/next", 2, true));
        GitlabTestReport testReport = new GitlabTestReport(1.0, 2, 1, 1, 0, 0, List.of());
        when(gitlab.getPipelineTestReport("group/repo", "42")).thenReturn(Optional.of(testReport));

        var context = collector.collect("group/repo", "pipeline-url", null);

        assertThat(context.pipeline()).isSameAs(pipeline);
        assertThat(context.jobs()).containsExactlyElementsOf(jobs);
        assertThat(context.jobsTruncated()).isTrue();
        assertThat(context.totalJobsFetched()).isEqualTo(2);
        assertThat(context.testReport()).isSameAs(testReport);
        verify(gitlab).getPipelineJobs("group/repo", "pipeline-url", false, 2);
    }

    @Test
    void choosesLatestFailedMergeRequestPipeline() {
        Pipeline successful = pipeline(43L, "success");
        Pipeline failed = pipeline(42L, "failed");
        when(gitlab.listMergeRequestPipelines("group/repo", "!17", new GitlabPageRequest(1, 20)))
                .thenReturn(List.of(successful, failed));
        when(gitlab.getPipelineJobs("group/repo", "42", false, 2))
                .thenReturn(new GitlabPage<>(List.of(), null, 0, false));

        var context = collector.collect("group/repo", null, "!17");

        assertThat(context.pipeline()).isSameAs(failed);
        verify(gitlab).getPipelineJobs("group/repo", "42", false, 2);
    }

    @Test
    void collectsFailedJobTraceArtifactsAndJunitReportsWhenRequested() {
        Pipeline pipeline = pipeline(42L, "failed");
        Job failedJob = job(7L);
        ArtifactFile junit = new ArtifactFile("junit.xml", "reports/jest-junit.xml", "file", 128L, "100644");
        when(gitlab.getPipeline("group/repo", "42")).thenReturn(pipeline);
        when(gitlab.getPipelineJobs("group/repo", "42", false, 2))
                .thenReturn(new GitlabPage<>(List.of(failedJob), null, 1, false));
        when(gitlab.getPipelineTestReport("group/repo", "42")).thenReturn(Optional.empty());
        when(gitlab.getJobTraceTail("group/repo", "7", 4096)).thenReturn("failed trace");
        when(gitlab.listJobArtifacts("group/repo", "7", null, true, new GitlabPageRequest(1, 50)))
                .thenReturn(List.of(junit));
        when(gitlab.getJobArtifactFile("group/repo", "7", "reports/jest-junit.xml", 8192))
                .thenReturn("<testsuite/>");
        PipelineCollectionOptions options = new PipelineCollectionOptions(true, 4096, true, 50, 5, 8192);

        var context = collector.collect("group/repo", "42", null, options);

        assertThat(context.traces()).containsEntry(7L, "failed trace");
        assertThat(context.artifacts()).containsEntry(7L, List.of(junit));
        assertThat(context.junitReports()).containsEntry("7:reports/jest-junit.xml", "<testsuite/>");
        assertThat(context.buildSignals()).contains("node");
    }

    @Test
    void skipsOversizedJunitReportAndRecordsWarning() {
        Pipeline pipeline = pipeline(42L, "failed");
        Job failedJob = job(7L);
        ArtifactFile junit = new ArtifactFile("junit.xml", "reports/junit.xml", "file", 150_000_000L, "100644");
        when(gitlab.getPipeline("group/repo", "42")).thenReturn(pipeline);
        when(gitlab.getPipelineJobs("group/repo", "42", false, 2))
                .thenReturn(new GitlabPage<>(List.of(failedJob), null, 1, false));
        when(gitlab.getPipelineTestReport("group/repo", "42")).thenReturn(Optional.empty());
        when(gitlab.getJobTraceTail("group/repo", "7", 4096)).thenReturn("failed trace");
        when(gitlab.listJobArtifacts("group/repo", "7", null, true, new GitlabPageRequest(1, 50)))
                .thenReturn(List.of(junit));
        when(gitlab.getJobArtifactFile("group/repo", "7", "reports/junit.xml", 8192))
                .thenThrow(new GitlabDownloadLimitException(
                        URI.create("https://gitlab.example/artifacts/reports/junit.xml"), 100_000_000L));
        PipelineCollectionOptions options = new PipelineCollectionOptions(true, 4096, true, 50, 5, 8192);

        var context = collector.collect("group/repo", "42", null, options);

        assertThat(context.artifacts()).containsEntry(7L, List.of(junit));
        assertThat(context.junitReports()).isEmpty();
        assertThat(context.warnings())
                .containsExactly("Skipped artifact report reports/junit.xml because it exceeds "
                        + "the configured download limit of 100000000 bytes.");
    }

    @Test
    void collectsJobsFromBoundedDownstreamPipelineGraph() {
        Pipeline root = pipeline(42L, "failed");
        Pipeline child = new Pipeline(
                84L,
                1L,
                12L,
                "def456",
                "main",
                "failed",
                "pipeline",
                null,
                null,
                null,
                null,
                30L,
                1L,
                "https://gitlab.example/child/-/pipelines/84");
        PipelineBridge bridge = new PipelineBridge(700L, "child pipeline", "test", "failed", child);
        Job rootJob = job(7L);
        Job childJob = job(8L);
        DefaultPipelineContextCollector graphCollector = new DefaultPipelineContextCollector(gitlab, 5, 3, 2);

        when(gitlab.getPipeline("group/repo", "42")).thenReturn(root);
        when(gitlab.getPipelineBridges("group/repo", "42", 2))
                .thenReturn(new GitlabPage<>(List.of(bridge), null, 1, false));
        when(gitlab.getPipelineBridges("12", "84", 1)).thenReturn(new GitlabPage<>(List.of(), null, 0, false));
        when(gitlab.getPipelineJobs("group/repo", "42", false, 5))
                .thenReturn(new GitlabPage<>(List.of(rootJob), null, 1, false));
        when(gitlab.getPipelineJobs("12", "84", false, 4))
                .thenReturn(new GitlabPage<>(List.of(childJob), null, 1, false));
        when(gitlab.getPipelineTestReport("group/repo", "42")).thenReturn(Optional.empty());

        var context = graphCollector.collect(
                "group/repo", "42", null, new PipelineCollectionOptions(false, 1, false, 1, 1, 1));

        assertThat(context.jobs()).containsExactly(rootJob, childJob);
        assertThat(context.graph().nodes()).hasSize(2);
        assertThat(context.graph().nodes().get(1).projectId()).isEqualTo("12");
        assertThat(context.graph().edges()).singleElement().satisfies(edge -> {
            assertThat(edge.sourcePipelineId()).isEqualTo(42);
            assertThat(edge.targetPipelineId()).isEqualTo(84);
            assertThat(edge.bridgeJobId()).isEqualTo(700);
        });
        assertThat(context.graph().truncated()).isFalse();
    }

    private static Pipeline pipeline(Long id, String status) {
        return new Pipeline(
                id,
                1L,
                11L,
                "abc123",
                "main",
                status,
                "push",
                null,
                null,
                null,
                null,
                60L,
                1L,
                "https://gitlab.example/pipelines/" + id);
    }

    private static Job job(Long id) {
        return new Job(
                id,
                "job-" + id,
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
                1.0,
                1.0,
                List.of());
    }
}
