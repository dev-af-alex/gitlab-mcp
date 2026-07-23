package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import com.alexaf.gitlabmcp.domain.GitlabCapability;
import com.alexaf.gitlabmcp.domain.GitlabVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitlabCapabilityResolverTest {

    private final GitlabCapabilityResolver resolver = new GitlabCapabilityResolver();

    @Test
    void supportsGitlab15_1EnterpriseEditionAsBaseline() {
        var info = resolver.resolve("15.1.0-ee", "abc123");

        assertThat(info.version()).isEqualTo(new GitlabVersion(15, 1, 0, "ee", "15.1.0-ee"));
        assertThat(info.supported()).isTrue();
        assertThat(info.minimumSupportedVersion()).isEqualTo(GitlabVersion.of(15, 1, 0));
        assertThat(info.capabilities()).containsExactly(
                GitlabCapability.MERGE_REQUEST_CHANGES,
                GitlabCapability.PIPELINE_TEST_REPORT,
                GitlabCapability.PIPELINE_BRIDGES,
                GitlabCapability.SINGLE_ARTIFACT_FILE);
    }

    @Test
    void enablesMergeRequestDiffsStartingWithGitlab15_7() {
        assertThat(resolver.resolve("15.6.9-ee", null).capabilities())
                .doesNotContain(GitlabCapability.MERGE_REQUEST_DIFFS);
        assertThat(resolver.resolve("15.7.0-ee", null).capabilities())
                .contains(GitlabCapability.MERGE_REQUEST_DIFFS);
    }

    @Test
    void enablesArtifactTreeStartingWithGitlab18_8() {
        assertThat(resolver.resolve("18.7.9-ee", null).capabilities())
                .doesNotContain(GitlabCapability.ARTIFACT_TREE);
        assertThat(resolver.resolve("18.8.0-ee", null).capabilities())
                .contains(GitlabCapability.ARTIFACT_TREE);
    }

    @Test
    void reportsOlderGitlabAsUnsupported() {
        assertThat(resolver.resolve("15.0.5-ee", null).supported()).isFalse();
    }
}
