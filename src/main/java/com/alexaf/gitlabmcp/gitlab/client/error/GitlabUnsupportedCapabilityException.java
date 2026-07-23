package com.alexaf.gitlabmcp.gitlab.client.error;

public final class GitlabUnsupportedCapabilityException extends GitlabApiException {

    private final String capability;

    public GitlabUnsupportedCapabilityException(String capability, Throwable cause) {
        super("GitLab server does not support capability: " + capability, null, null, cause);
        this.capability = capability;
    }

    public String capability() {
        return capability;
    }
}
