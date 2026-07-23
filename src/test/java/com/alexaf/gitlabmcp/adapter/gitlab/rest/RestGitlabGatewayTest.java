package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
}
