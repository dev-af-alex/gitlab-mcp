package com.alexaf.gitlabmcp.gitlab.client.error;

import java.net.URI;

public final class GitlabDownloadLimitException extends GitlabApiException {

    private final long maxBytes;

    public GitlabDownloadLimitException(URI requestUri, long maxBytes) {
        super(
                "GitLab response exceeds configured download limit of " + maxBytes + " bytes: " + requestUri,
                requestUri,
                null);
        this.maxBytes = maxBytes;
    }

    public long maxBytes() {
        return maxBytes;
    }
}
