package com.alexaf.gitlabmcp.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record GitlabServerInfo(
        GitlabVersion version,
        String revision,
        Set<GitlabCapability> capabilities,
        GitlabVersion minimumSupportedVersion,
        boolean supported) {

    public GitlabServerInfo {
        capabilities = capabilities.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }
}
