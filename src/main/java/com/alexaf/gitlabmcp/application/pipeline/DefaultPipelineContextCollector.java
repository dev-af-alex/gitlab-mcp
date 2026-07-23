package com.alexaf.gitlabmcp.application.pipeline;

import com.alexaf.gitlabmcp.domain.GitlabPage;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.alexaf.gitlabmcp.port.GitlabGateway;
import com.alexaf.gitlabmcp.port.PipelineContextCollector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class DefaultPipelineContextCollector implements PipelineContextCollector {

    private final GitlabGateway gitlab;
    private final int maxJobs;

    public DefaultPipelineContextCollector(GitlabGateway gitlab, GitlabProperties properties) {
        this(gitlab, properties.maxJobs());
    }

    public DefaultPipelineContextCollector(GitlabGateway gitlab, int maxJobs) {
        this.gitlab = gitlab;
        this.maxJobs = Math.max(1, maxJobs);
    }

    @Override
    public PipelineContext collect(String projectId, String pipelineId, String mergeRequestIid) {
        Pipeline pipeline = resolvePipeline(projectId, pipelineId, mergeRequestIid);
        String jobsPipelineId = StringUtils.hasText(pipelineId)
                ? pipelineId
                : String.valueOf(pipeline.id());
        GitlabPage<Job> jobs = gitlab.getPipelineJobs(
                projectId,
                jobsPipelineId,
                false,
                maxJobs);
        return new PipelineContext(
                pipeline,
                jobs.items(),
                jobs.truncated(),
                jobs.totalFetched());
    }

    private Pipeline resolvePipeline(String projectId, String pipelineId, String mergeRequestIid) {
        if (StringUtils.hasText(pipelineId)) {
            return gitlab.getPipeline(projectId, pipelineId);
        }
        if (StringUtils.hasText(mergeRequestIid)) {
            List<Pipeline> pipelines = gitlab.listMergeRequestPipelines(
                    projectId,
                    mergeRequestIid,
                    new GitlabPageRequest(1, 20));
            return pipelines.stream()
                    .filter(pipeline -> "failed".equals(pipeline.status())
                            || "canceled".equals(pipeline.status()))
                    .findFirst()
                    .or(() -> pipelines.stream().findFirst())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No pipelines found for merge request: " + mergeRequestIid));
        }
        throw new IllegalArgumentException("Either pipelineId or mergeRequestIid must be set");
    }
}
