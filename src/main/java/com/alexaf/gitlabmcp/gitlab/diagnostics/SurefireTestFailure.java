package com.alexaf.gitlabmcp.gitlab.diagnostics;

public record SurefireTestFailure(
        String methodName, String kind, String exceptionType, String message, String sourceLocation) {}
