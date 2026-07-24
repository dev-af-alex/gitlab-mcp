package com.alexaf.gitlabmcp.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;
import com.alexaf.gitlabmcp.gitlab.dto.GitlabTestReport;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;

public record PipelineContext(
        Pipeline pipeline,
        List<Job> jobs,
        Map<Long, String> traces,
        Map<String, String> junitReports,
        Map<Long, List<ArtifactFile>> artifacts,
        GitlabTestReport testReport,
        boolean jobsTruncated,
        int totalJobsFetched,
        PipelineGraph graph,
        Set<String> buildSignals,
        List<String> warnings) {

    public PipelineContext {
        jobs = List.copyOf(jobs);
        traces = Map.copyOf(traces);
        junitReports = Map.copyOf(junitReports);
        artifacts = artifacts.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        graph = graph == null ? PipelineGraph.root("", pipeline) : graph;
        buildSignals = Set.copyOf(buildSignals);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public PipelineContext(
            Pipeline pipeline,
            List<Job> jobs,
            Map<Long, String> traces,
            Map<String, String> junitReports,
            Map<Long, List<ArtifactFile>> artifacts,
            GitlabTestReport testReport,
            boolean jobsTruncated,
            int totalJobsFetched,
            PipelineGraph graph,
            Set<String> buildSignals) {
        this(
                pipeline,
                jobs,
                traces,
                junitReports,
                artifacts,
                testReport,
                jobsTruncated,
                totalJobsFetched,
                graph,
                buildSignals,
                List.of());
    }

    public PipelineContext(
            Pipeline pipeline,
            List<Job> jobs,
            Map<Long, String> traces,
            Map<String, String> junitReports,
            Map<Long, List<ArtifactFile>> artifacts,
            GitlabTestReport testReport,
            boolean jobsTruncated,
            int totalJobsFetched,
            PipelineGraph graph) {
        this(
                pipeline,
                jobs,
                traces,
                junitReports,
                artifacts,
                testReport,
                jobsTruncated,
                totalJobsFetched,
                graph,
                Set.of(),
                List.of());
    }

    public PipelineContext(
            Pipeline pipeline,
            List<Job> jobs,
            Map<Long, String> traces,
            Map<String, String> junitReports,
            Map<Long, List<ArtifactFile>> artifacts,
            GitlabTestReport testReport,
            boolean jobsTruncated,
            int totalJobsFetched) {
        this(
                pipeline,
                jobs,
                traces,
                junitReports,
                artifacts,
                testReport,
                jobsTruncated,
                totalJobsFetched,
                null,
                Set.of());
    }

    public PipelineContext(Pipeline pipeline, List<Job> jobs, boolean jobsTruncated, int totalJobsFetched) {
        this(pipeline, jobs, Map.of(), Map.of(), Map.of(), null, jobsTruncated, totalJobsFetched);
    }

    public PipelineContext(
            Pipeline pipeline,
            List<Job> jobs,
            GitlabTestReport testReport,
            boolean jobsTruncated,
            int totalJobsFetched) {
        this(pipeline, jobs, Map.of(), Map.of(), Map.of(), testReport, jobsTruncated, totalJobsFetched);
    }

    public PipelineContext withExecutionData(Map<Long, String> traces, Map<String, String> junitReports) {
        return withExecutionData(traces, junitReports, artifacts);
    }

    public PipelineContext withExecutionData(
            Map<Long, String> traces, Map<String, String> junitReports, Map<Long, List<ArtifactFile>> artifacts) {
        return new PipelineContext(
                pipeline,
                jobs,
                traces,
                junitReports,
                artifacts,
                testReport,
                jobsTruncated,
                totalJobsFetched,
                graph,
                buildSignals,
                warnings);
    }
}
