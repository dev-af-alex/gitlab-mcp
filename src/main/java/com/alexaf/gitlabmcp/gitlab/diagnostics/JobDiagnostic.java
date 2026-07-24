package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.util.List;

import com.alexaf.gitlabmcp.gitlab.dto.RunnerInfo;

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
        List<String> nextSteps,
        RunnerInfo runner) {}
