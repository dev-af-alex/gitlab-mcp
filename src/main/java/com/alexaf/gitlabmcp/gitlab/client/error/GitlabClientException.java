package com.alexaf.gitlabmcp.gitlab.client.error;

import java.net.URI;

public final class GitlabClientException extends GitlabApiException {

    public GitlabClientException(URI requestUri, int statusCode, Throwable cause) {
        super("GitLab client error for: " + requestUri + " (" + statusCode + ")", requestUri, statusCode, cause);
    }

    public GitlabClientException(URI requestUri, int statusCode) {
        this(requestUri, statusCode, null);
    }
}
