package com.alexaf.gitlabmcp.application.pipeline;

import com.alexaf.gitlabmcp.domain.GitlabPage;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.domain.PipelineCollectionOptions;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabNotFoundException;
import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.alexaf.gitlabmcp.port.GitlabGateway;
import com.alexaf.gitlabmcp.port.PipelineContextCollector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    public PipelineContext collect(
            String projectId,
            String pipelineId,
            String mergeRequestIid,
            PipelineCollectionOptions options
    ) {
        Pipeline pipeline = resolvePipeline(projectId, pipelineId, mergeRequestIid);
        String jobsPipelineId = StringUtils.hasText(pipelineId)
                ? pipelineId
                : String.valueOf(pipeline.id());
        GitlabPage<Job> jobs = gitlab.getPipelineJobs(
                projectId,
                jobsPipelineId,
                false,
                maxJobs);
        var testReport = gitlab.getPipelineTestReport(
                projectId,
                String.valueOf(pipeline.id()));
        Map<Long, String> traces = new LinkedHashMap<>();
        Map<Long, List<ArtifactFile>> artifacts = new LinkedHashMap<>();
        Map<String, String> junitReports = new LinkedHashMap<>();
        for (Job job : jobs.items()) {
            if (!"failed".equals(job.status())) {
                continue;
            }
            collectTrace(projectId, job, options, traces);
            collectArtifacts(projectId, job, options, artifacts, junitReports);
        }
        return new PipelineContext(
                pipeline,
                jobs.items(),
                traces,
                junitReports,
                artifacts,
                testReport.orElse(null),
                jobs.truncated(),
                jobs.totalFetched());
    }

    private void collectTrace(
            String projectId,
            Job job,
            PipelineCollectionOptions options,
            Map<Long, String> traces
    ) {
        if (!options.includeTraces()) {
            return;
        }
        traces.put(job.id(), gitlab.getJobTraceTail(
                projectId,
                String.valueOf(job.id()),
                Math.max(1, options.maxTraceBytes())));
    }

    private void collectArtifacts(
            String projectId,
            Job job,
            PipelineCollectionOptions options,
            Map<Long, List<ArtifactFile>> artifacts,
            Map<String, String> junitReports
    ) {
        if (!options.includeArtifacts()) {
            return;
        }
        List<ArtifactFile> files;
        try {
            files = gitlab.listJobArtifacts(
                    projectId,
                    String.valueOf(job.id()),
                    null,
                    true,
                    new GitlabPageRequest(1, Math.max(1, options.maxArtifactFilesPerJob())));
        } catch (GitlabNotFoundException noArtifacts) {
            return;
        }
        artifacts.put(job.id(), files);
        for (ArtifactFile file : files) {
            if (junitReports.size() >= Math.max(1, options.maxJunitReports())
                    || !isJunitReport(file.path())) {
                continue;
            }
            String content = gitlab.getJobArtifactFile(
                    projectId,
                    String.valueOf(job.id()),
                    file.path(),
                    Math.max(1, options.maxReportBytes()));
            if (StringUtils.hasText(content)) {
                junitReports.put(job.id() + ":" + file.path(), content);
            }
        }
    }

    private boolean isJunitReport(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        return normalized.endsWith(".xml")
                && (fileName.startsWith("test-")
                || normalized.contains("junit")
                || normalized.contains("surefire-reports")
                || normalized.contains("test-results")
                || normalized.contains("pytest")
                || normalized.contains("jest"));
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
