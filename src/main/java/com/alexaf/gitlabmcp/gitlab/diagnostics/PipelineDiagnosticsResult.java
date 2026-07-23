package com.alexaf.gitlabmcp.gitlab.diagnostics;

import com.alexaf.gitlabmcp.domain.Finding;
import com.alexaf.gitlabmcp.domain.PipelineGraph;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;

import java.util.List;

public record PipelineDiagnosticsResult(
        Pipeline pipeline,
        String summary,
        List<JobDiagnostic> failedJobs,
        List<JobSummary> otherNotSuccessfulJobs,
        boolean tracesIncluded,
        boolean rawTracesIncluded,
        boolean artifactHintsIncluded,
        String warning,
        boolean detailsIncluded,
        List<Finding> findings,
        List<String> analyzers,
        PipelineGraph graph
) {

    public PipelineDiagnosticsResult {
        failedJobs = List.copyOf(failedJobs);
        otherNotSuccessfulJobs = List.copyOf(otherNotSuccessfulJobs);
        findings = List.copyOf(findings);
        analyzers = List.copyOf(analyzers);
        graph = graph == null ? PipelineGraph.root("", pipeline) : graph;
    }

    public PipelineDiagnosticsResult(
            Pipeline pipeline,
            String summary,
            List<JobDiagnostic> failedJobs,
            List<JobSummary> otherNotSuccessfulJobs,
            boolean tracesIncluded,
            boolean rawTracesIncluded,
            boolean artifactHintsIncluded,
            String warning,
            boolean detailsIncluded,
            List<Finding> findings,
            List<String> analyzers
    ) {
        this(pipeline, summary, failedJobs, otherNotSuccessfulJobs, tracesIncluded, rawTracesIncluded,
                artifactHintsIncluded, warning, detailsIncluded, findings, analyzers, null);
    }

    public PipelineDiagnosticsResult(
            Pipeline pipeline,
            String summary,
            List<JobDiagnostic> failedJobs,
            List<JobSummary> otherNotSuccessfulJobs,
            boolean tracesIncluded,
            boolean rawTracesIncluded,
            boolean artifactHintsIncluded,
            String warning,
            boolean detailsIncluded
    ) {
        this(pipeline, summary, failedJobs, otherNotSuccessfulJobs, tracesIncluded, rawTracesIncluded,
                artifactHintsIncluded, warning, detailsIncluded, List.of(), List.of(), null);
    }

    public PipelineDiagnosticsResult(
            Pipeline pipeline,
            String summary,
            List<JobDiagnostic> failedJobs,
            List<JobSummary> otherNotSuccessfulJobs,
            boolean tracesIncluded,
            boolean rawTracesIncluded,
            boolean artifactHintsIncluded,
            String warning) {
        this(pipeline, summary, failedJobs, otherNotSuccessfulJobs, tracesIncluded, rawTracesIncluded,
                artifactHintsIncluded, warning, false, List.of(), List.of(), null);
    }
}
