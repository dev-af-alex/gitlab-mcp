package com.alexaf.gitlabmcp.gitlab.client.error;

import java.net.URI;

public final class GitlabUnauthorizedException extends GitlabApiException {

    public GitlabUnauthorizedException(URI requestUri, Throwable cause) {
        super("GitLab token is missing, expired, or invalid", requestUri, 401, cause);
    }

    public GitlabUnauthorizedException(URI requestUri) {
        this(requestUri, null);
    }
}
