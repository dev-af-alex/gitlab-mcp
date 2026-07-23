package com.alexaf.gitlabmcp.gitlab.client.error;

import java.net.URI;

public final class GitlabNotFoundException extends GitlabApiException {

    public GitlabNotFoundException(URI requestUri, Throwable cause) {
        super("GitLab resource not found: " + requestUri, requestUri, 404, cause);
    }

    public GitlabNotFoundException(URI requestUri) {
        this(requestUri, null);
    }
}
