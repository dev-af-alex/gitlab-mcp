package com.alexaf.gitlabmcp.gitlab.client.error;

import java.net.URI;

public final class GitlabForbiddenException extends GitlabApiException {

    public GitlabForbiddenException(URI requestUri, Throwable cause) {
        super("GitLab access denied for: " + requestUri, requestUri, 403, cause);
    }

    public GitlabForbiddenException(URI requestUri) {
        this(requestUri, null);
    }
}
