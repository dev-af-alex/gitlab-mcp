package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import com.alexaf.gitlabmcp.domain.GitlabCapability;
import com.alexaf.gitlabmcp.gitlab.dto.GitlabTestReport;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GitlabContractFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GitlabCapabilityResolver resolver = new GitlabCapabilityResolver();

    @ParameterizedTest
    @CsvSource({
            "15.1.json, false, false",
            "16.11.json, true, false",
            "17.11.json, true, false",
            "18.11.json, true, true",
            "19.2.json, true, true"
    })
    void decodesSanitizedContractsAcrossSupportedGitlabMajors(
            String fixture,
            boolean mergeRequestDiffs,
            boolean artifactTree
    ) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/gitlab-contract/" + fixture)) {
            assertThat(input).as("fixture %s", fixture).isNotNull();
            JsonNode root = objectMapper.readTree(input);
            var version = objectMapper.treeToValue(
                    root.path("version"),
                    GitlabServerInfoProvider.VersionResponse.class);
            Pipeline pipeline = objectMapper.treeToValue(root.path("pipeline"), Pipeline.class);
            Job job = objectMapper.treeToValue(root.path("job"), Job.class);
            GitlabTestReport report = objectMapper.treeToValue(
                    root.path("test_report"),
                    GitlabTestReport.class);
            var serverInfo = resolver.resolve(version.version(), version.revision());

            assertThat(serverInfo.supported()).isTrue();
            assertThat(serverInfo.capabilities().contains(GitlabCapability.MERGE_REQUEST_DIFFS))
                    .isEqualTo(mergeRequestDiffs);
            assertThat(serverInfo.capabilities().contains(GitlabCapability.ARTIFACT_TREE))
                    .isEqualTo(artifactTree);
            assertThat(pipeline.id()).isPositive();
            assertThat(job.id()).isPositive();
            assertThat(report.totalCount()).isPositive();
        }
    }
}
