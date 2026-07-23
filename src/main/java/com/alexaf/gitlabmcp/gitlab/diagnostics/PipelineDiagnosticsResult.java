package com.alexaf.gitlabmcp.gitlab.diagnostics;

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
        boolean detailsIncluded
) {

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
                artifactHintsIncluded, warning, false);
    }
}
