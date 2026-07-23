package com.alexaf.gitlabmcp.gitlab.client.error;

import java.net.URI;
import java.time.Duration;

public final class GitlabRateLimitedException extends GitlabApiException {

    private final Duration retryAfter;

    public GitlabRateLimitedException(URI requestUri, Duration retryAfter, Throwable cause) {
        super("GitLab rate limit exceeded for: " + requestUri, requestUri, 429, cause);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
