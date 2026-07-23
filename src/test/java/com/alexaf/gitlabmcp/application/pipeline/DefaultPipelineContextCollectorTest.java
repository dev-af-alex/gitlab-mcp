package com.alexaf.gitlabmcp.application.pipeline;

import com.alexaf.gitlabmcp.domain.GitlabPage;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.alexaf.gitlabmcp.port.GitlabGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultPipelineContextCollectorTest {

    private final GitlabGateway gitlab = mock(GitlabGateway.class);
    private final DefaultPipelineContextCollector collector =
            new DefaultPipelineContextCollector(gitlab, 2);

    @Test
    void collectsAllJobsUpToConfiguredLimitAndPreservesTruncation() {
        Pipeline pipeline = pipeline(42L, "failed");
        List<Job> jobs = List.of(job(1L), job(2L));
        when(gitlab.getPipeline("group/repo", "pipeline-url")).thenReturn(pipeline);
        when(gitlab.getPipelineJobs("group/repo", "pipeline-url", false, 2))
                .thenReturn(new GitlabPage<>(jobs, "https://gitlab.example/next", 2, true));

        var context = collector.collect("group/repo", "pipeline-url", null);

        assertThat(context.pipeline()).isSameAs(pipeline);
        assertThat(context.jobs()).containsExactlyElementsOf(jobs);
        assertThat(context.jobsTruncated()).isTrue();
        assertThat(context.totalJobsFetched()).isEqualTo(2);
        verify(gitlab).getPipelineJobs("group/repo", "pipeline-url", false, 2);
    }

    @Test
    void choosesLatestFailedMergeRequestPipeline() {
        Pipeline successful = pipeline(43L, "success");
        Pipeline failed = pipeline(42L, "failed");
        when(gitlab.listMergeRequestPipelines(
                "group/repo", "!17", new GitlabPageRequest(1, 20)))
                .thenReturn(List.of(successful, failed));
        when(gitlab.getPipelineJobs("group/repo", "42", false, 2))
                .thenReturn(new GitlabPage<>(List.of(), null, 0, false));

        var context = collector.collect("group/repo", null, "!17");

        assertThat(context.pipeline()).isSameAs(failed);
        verify(gitlab).getPipelineJobs("group/repo", "42", false, 2);
    }

    private static Pipeline pipeline(Long id, String status) {
        return new Pipeline(id, 1L, 11L, "abc123", "main", status, "push",
                null, null, null, null, 60L, 1L, "https://gitlab.example/pipelines/" + id);
    }

    private static Job job(Long id) {
        return new Job(id, "job-" + id, "test", "failed", "script_failure", null,
                "main", false, false, null, null, null, 1.0, 1.0, List.of());
    }
}
