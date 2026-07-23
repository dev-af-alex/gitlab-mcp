package com.alexaf.gitlabmcp.gitlab.diagnostics;

public record JobSummary(
        Long id,
        String name,
        String stage,
        String status,
        String failureReason,
        String webUrl
) {
}
