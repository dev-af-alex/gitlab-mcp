package com.alexaf.gitlabmcp.gitlab.client.error;

import java.net.URI;

public final class GitlabTransportException extends GitlabApiException {

    public GitlabTransportException(URI requestUri, Throwable cause) {
        super("Unable to request GitLab resource: " + requestUri, requestUri, null, cause);
    }
}
