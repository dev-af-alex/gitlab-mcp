package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class RestGitlabGatewayTest {

    private MockRestServiceServer server;
    private RestGitlabGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GitlabApiClient client = new GitlabApiClient(
                new GitlabProperties("https://gitlab.example", "token", List.of(), 20, 100),
                new ObjectMapper(),
                builder);
        gateway = new RestGitlabGateway(client);
    }

    @Test
    void readsMergeRequestChangesThroughGitlab15CompatibleEndpoint() {
        expectVersion("15.1.0-ee");
        server.expect(once(), requestTo(
                        "https://gitlab.example/api/v4/projects/group%2Frepo/merge_requests/17/changes"))
                .andRespond(withSuccess("""
                        {
                          "id": 101,
                          "iid": 17,
                          "changes": [
                            {"old_path": "a.txt", "new_path": "b.txt", "diff": "@@"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var changes = gateway.getMergeRequestChanges("group/repo", "!17");

        assertThat(changes.iid()).isEqualTo(17);
        assertThat(changes.changes()).singleElement()
                .satisfies(change -> assertThat(change.newPath()).isEqualTo("b.txt"));
        server.verify();
    }

    @Test
    void usesPaginatedDiffEndpointOnGitlab15_7AndNewer() {
        expectVersion("15.7.0-ee");
        server.expect(once(), requestTo(
                        "https://gitlab.example/api/v4/projects/group%2Frepo/merge_requests/17/diffs"
                                + "?page=1&per_page=100"))
                .andRespond(withSuccess("""
                        [{"old_path": "a.txt", "new_path": "b.txt", "diff": "@@"}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        "https://gitlab.example/api/v4/projects/group%2Frepo/merge_requests/17"))
                .andRespond(withSuccess("""
                        {
                          "id": 101,
                          "iid": 17,
                          "project_id": 3,
                          "title": "Change",
                          "state": "opened",
                          "target_branch": "main",
                          "source_branch": "feature"
                        }
                        """, MediaType.APPLICATION_JSON));

        var changes = gateway.getMergeRequestChanges("group/repo", "!17");

        assertThat(changes.title()).isEqualTo("Change");
        assertThat(changes.changes()).singleElement()
                .satisfies(change -> assertThat(change.newPath()).isEqualTo("b.txt"));
        server.verify();
    }

    @Test
    void fallsBackToLegacyChangesWhenDiffEndpointIsUnavailable() {
        expectVersion("18.8.0-ee");
        server.expect(once(), requestTo(
                        "https://gitlab.example/api/v4/projects/group%2Frepo/merge_requests/17/diffs"
                                + "?page=1&per_page=100"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(once(), requestTo(
                        "https://gitlab.example/api/v4/projects/group%2Frepo/merge_requests/17/changes"))
                .andRespond(withSuccess("""
                        {"id": 101, "iid": 17, "changes": []}
                        """, MediaType.APPLICATION_JSON));

        var changes = gateway.getMergeRequestChanges("group/repo", "!17");

        assertThat(changes.iid()).isEqualTo(17);
        server.verify();
    }

    @Test
    void listsPipelineJobsWithNormalizedIdsAndPagination() {
        server.expect(once(), requestTo(
                        "https://gitlab.example/api/v4/projects/group%2Frepo/pipelines/42/jobs"
                                + "?include_retried=false&page=2&per_page=30"))
                .andRespond(withSuccess("""
                        [{"id": 7, "name": "test", "status": "failed"}]
                        """, MediaType.APPLICATION_JSON));

        var jobs = gateway.listPipelineJobs(
                "group/repo",
                "pipeline #42",
                false,
                new GitlabPageRequest(2, 30));

        assertThat(jobs).singleElement()
                .satisfies(job -> {
                    assertThat(job.id()).isEqualTo(7);
                    assertThat(job.name()).isEqualTo("test");
                });
        server.verify();
    }

    @Test
    void listsArtifactsFromZipOnGitlab15_1() throws Exception {
        expectVersion("15.1.0-ee");
        server.expect(once(), requestTo(
                        "https://gitlab.example/api/v4/projects/group%2Frepo/jobs/7/artifacts"))
                .andRespond(withSuccess(
                        zip("target/report.xml"),
                        MediaType.APPLICATION_OCTET_STREAM));

        var artifacts = gateway.listJobArtifacts(
                "group/repo",
                "7",
                null,
                true,
                new GitlabPageRequest(1, 20));

        assertThat(artifacts).singleElement()
                .satisfies(artifact -> assertThat(artifact.path()).isEqualTo("target/report.xml"));
        server.verify();
    }

    @Test
    void listsArtifactsFromMetadataOnGitlab18_8() {
        expectVersion("18.8.0-ee");
        server.expect(once(), requestTo(
                        "https://gitlab.example/api/v4/projects/group%2Frepo/jobs/7/artifacts/tree"
                                + "?recursive=true&page=1&per_page=20"))
                .andRespond(withSuccess("""
                        [{
                          "name": "report.xml",
                          "path": "target/report.xml",
                          "type": "file",
                          "size": 42,
                          "mode": "100644"
                        }]
                        """, MediaType.APPLICATION_JSON));

        var artifacts = gateway.listJobArtifacts(
                "group/repo",
                "7",
                null,
                true,
                new GitlabPageRequest(1, 20));

        assertThat(artifacts).singleElement()
                .satisfies(artifact -> {
                    assertThat(artifact.path()).isEqualTo("target/report.xml");
                    assertThat(artifact.size()).isEqualTo(42);
                });
        server.verify();
    }

    @Test
    void readsAndCachesGitlabServerInformation() {
        expectVersion("15.1.0-ee");

        var first = gateway.getServerInfo();
        var second = gateway.getServerInfo();

        assertThat(first).isSameAs(second);
        assertThat(first.version().raw()).isEqualTo("15.1.0-ee");
        assertThat(first.supported()).isTrue();
        server.verify();
    }

    private void expectVersion(String version) {
        server.expect(once(), requestTo("https://gitlab.example/api/v4/version"))
                .andRespond(withSuccess("""
                        {"version": "%s", "revision": "abc123"}
                        """.formatted(version), MediaType.APPLICATION_JSON));
    }

    private byte[] zip(String... entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(("content for " + entry).getBytes());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
