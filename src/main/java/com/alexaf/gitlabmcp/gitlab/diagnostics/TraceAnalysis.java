package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.util.List;

public record TraceAnalysis(
        String detectedCause,
        String confidence,
        List<String> evidence,
        List<String> nextSteps
) {
}
