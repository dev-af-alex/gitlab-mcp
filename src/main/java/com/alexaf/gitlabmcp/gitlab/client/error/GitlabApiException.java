package com.alexaf.gitlabmcp.gitlab.client.error;

import java.net.URI;

public abstract class GitlabApiException extends IllegalArgumentException {

    private final URI requestUri;
    private final Integer statusCode;

    protected GitlabApiException(String message, URI requestUri, Integer statusCode) {
        super(message);
        this.requestUri = requestUri;
        this.statusCode = statusCode;
    }

    protected GitlabApiException(String message, URI requestUri, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.requestUri = requestUri;
        this.statusCode = statusCode;
    }

    public URI requestUri() {
        return requestUri;
    }

    public Integer statusCode() {
        return statusCode;
    }
}
