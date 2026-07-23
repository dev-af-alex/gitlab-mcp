package com.alexaf.gitlabmcp.gitlab.diagnostics;

public record MavenTestFailure(
        String className,
        String methodName,
        String message,
        String expected,
        String actual
) {
}
