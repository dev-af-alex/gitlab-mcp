package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import com.alexaf.gitlabmcp.domain.GitlabCapability;
import com.alexaf.gitlabmcp.domain.GitlabServerInfo;
import com.alexaf.gitlabmcp.domain.GitlabVersion;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Component
public class GitlabCapabilityResolver {

    public static final GitlabVersion MINIMUM_SUPPORTED_VERSION = GitlabVersion.of(15, 1, 0);
    private static final GitlabVersion MERGE_REQUEST_DIFFS_VERSION = GitlabVersion.of(15, 7, 0);
    private static final GitlabVersion ARTIFACT_TREE_VERSION = GitlabVersion.of(18, 8, 0);

    public GitlabServerInfo resolve(String version, String revision) {
        GitlabVersion parsedVersion = GitlabVersion.parse(version);
        EnumSet<GitlabCapability> capabilities = EnumSet.of(
                GitlabCapability.MERGE_REQUEST_CHANGES,
                GitlabCapability.PIPELINE_TEST_REPORT,
                GitlabCapability.PIPELINE_BRIDGES,
                GitlabCapability.SINGLE_ARTIFACT_FILE);
        if (parsedVersion.isAtLeast(MERGE_REQUEST_DIFFS_VERSION)) {
            capabilities.add(GitlabCapability.MERGE_REQUEST_DIFFS);
        }
        if (parsedVersion.isAtLeast(ARTIFACT_TREE_VERSION)) {
            capabilities.add(GitlabCapability.ARTIFACT_TREE);
        }
        return new GitlabServerInfo(
                parsedVersion,
                revision,
                capabilities,
                MINIMUM_SUPPORTED_VERSION,
                parsedVersion.isAtLeast(MINIMUM_SUPPORTED_VERSION));
    }
}
