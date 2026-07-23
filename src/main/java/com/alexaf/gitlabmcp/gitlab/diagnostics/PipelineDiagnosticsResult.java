package com.alexaf.gitlabmcp.gitlab.diagnostics;

import com.alexaf.gitlabmcp.domain.Finding;
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
        List<String> analyzers
) {

    public PipelineDiagnosticsResult {
        failedJobs = List.copyOf(failedJobs);
        otherNotSuccessfulJobs = List.copyOf(otherNotSuccessfulJobs);
        findings = List.copyOf(findings);
        analyzers = List.copyOf(analyzers);
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
                artifactHintsIncluded, warning, detailsIncluded, List.of(), List.of());
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
                artifactHintsIncluded, warning, false, List.of(), List.of());
    }
}
