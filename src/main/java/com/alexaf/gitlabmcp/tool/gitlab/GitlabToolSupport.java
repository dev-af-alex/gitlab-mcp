package com.alexaf.gitlabmcp.tool.gitlab;

final class GitlabToolSupport {

    static final int DEFAULT_TRACE_BYTES = 60_000;

    private GitlabToolSupport() {
    }

    static int defaultMaxBytes(Integer maxBytes) {
        return maxBytes == null || maxBytes <= 0 ? DEFAULT_TRACE_BYTES : maxBytes;
    }

    static String artifactPath(String artifactPath) {
        if (artifactPath == null || artifactPath.isBlank()) {
            throw new IllegalArgumentException("artifactPath must be set");
        }
        String result = artifactPath.trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        if (result.isBlank()) {
            throw new IllegalArgumentException("artifactPath must be set");
        }
        return result;
    }
}
