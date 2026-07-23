package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.util.List;

public record JobDiagnostic(
        Long id,
        String name,
        String stage,
        String status,
        String failureReason,
        String webUrl,
        String detectedCause,
        String confidence,
        List<String> evidence,
        JobFailureSummary failureSummary,
        List<String> usefulArtifacts,
        String trace,
        boolean traceTruncated,
        List<String> nextSteps
) {
}
