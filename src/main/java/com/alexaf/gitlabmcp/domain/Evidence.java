package com.alexaf.gitlabmcp.domain;

public record Evidence(
        String source,
        Long jobId,
        String path,
        String message
) {
}
