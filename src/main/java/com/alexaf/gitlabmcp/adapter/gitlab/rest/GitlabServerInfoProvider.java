package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import com.alexaf.gitlabmcp.domain.GitlabServerInfo;
import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class GitlabServerInfoProvider {

    private final GitlabApiClient gitlab;
    private final GitlabCapabilityResolver capabilityResolver;
    private final AtomicReference<GitlabServerInfo> cached = new AtomicReference<>();

    public GitlabServerInfoProvider(
            GitlabApiClient gitlab,
            GitlabCapabilityResolver capabilityResolver
    ) {
        this.gitlab = gitlab;
        this.capabilityResolver = capabilityResolver;
    }

    public GitlabServerInfo get() {
        GitlabServerInfo current = cached.get();
        if (current != null) {
            return current;
        }
        VersionResponse response = gitlab.getObject("/version", VersionResponse.class);
        GitlabServerInfo resolved = capabilityResolver.resolve(response.version(), response.revision());
        cached.compareAndSet(null, resolved);
        return cached.get();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VersionResponse(String version, String revision) {
    }
}
