package com.alexaf.gitlabmcp.gitlab.client.error;

import java.net.URI;

public final class GitlabServerException extends GitlabApiException {

    public GitlabServerException(URI requestUri, int statusCode, Throwable cause) {
        super("GitLab server error for: " + requestUri + " (" + statusCode + ")",
                requestUri, statusCode, cause);
    }

    public GitlabServerException(URI requestUri, int statusCode) {
        this(requestUri, statusCode, null);
    }
}
