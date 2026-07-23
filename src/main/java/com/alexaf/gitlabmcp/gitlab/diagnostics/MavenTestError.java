package com.alexaf.gitlabmcp.gitlab.diagnostics;

public record MavenTestError(
        String className,
        String methodName,
        String errorType,
        String message,
        boolean contextCascade
) {
}
