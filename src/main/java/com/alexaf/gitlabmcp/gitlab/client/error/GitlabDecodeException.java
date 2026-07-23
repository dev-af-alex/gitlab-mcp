package com.alexaf.gitlabmcp.gitlab.client.error;

public final class GitlabDecodeException extends GitlabApiException {

    public GitlabDecodeException(String targetType, Throwable cause) {
        super("Unable to parse GitLab response" + targetType, null, null, cause);
    }
}
