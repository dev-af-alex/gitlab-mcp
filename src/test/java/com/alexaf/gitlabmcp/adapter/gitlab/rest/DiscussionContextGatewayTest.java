package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.alexaf.gitlabmcp.application.JsonResponseWriter;
import com.alexaf.gitlabmcp.application.discussion.DiscussionContextService;
import com.alexaf.gitlabmcp.domain.CodeContext.Status;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabDownloadLimitException;
import com.alexaf.gitlabmcp.tool.gitlab.GitlabMergeRequestTools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DiscussionContextGatewayTest {
    private static final String SHA = "a".repeat(40);
    private static final String MR_URL = "https://gitlab.example/api/v4/projects/group%2Frepo/merge_requests/17";
    private MockRestServiceServer server;
    private RestGitlabGateway gateway;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        configure(100_000_000, 100);
    }

    @Test
    void readsGitlab15DiscussionAndHistoricalVersionThroughMcpTool() throws Exception {
        expect(MR_URL + "/discussions/thread", discussion());
        expect(MR_URL + "/versions?page=1&per_page=20", "[" + version(false) + "]");
        expect(MR_URL + "/versions/8", version(true));
        var tools = new GitlabMergeRequestTools(
                gateway, new JsonResponseWriter(mapper), new DiscussionContextService(gateway));

        var result = mapper.readTree(tools.getMergeRequestDiscussionContext("group/repo", "!17", "thread", 0));

        assertThat(result.path("discussion")
                        .path("notes")
                        .get(0)
                        .path("position")
                        .path("head_sha")
                        .asText())
                .isEqualTo(SHA);
        assertThat(result.path("anchorNoteId").asLong()).isEqualTo(1);
        assertThat(result.path("context").path("status").asText()).isEqualTo("EXACT");
        assertThat(result.path("context").path("lines").get(0).path("text").asText())
                .isEqualTo("new code");
        assertThat(result.path("context")
                        .path("lines")
                        .get(0)
                        .path("highlighted")
                        .asBoolean())
                .isTrue();
        server.verify();
    }

    @Test
    void existingDiscussionListPreservesPositionAndOptionalFields() {
        expect(MR_URL + "/discussions?page=1&per_page=20", "[" + discussion() + "]");
        var notes = gateway.listMergeRequestDiscussions("group/repo", "17", new GitlabPageRequest(1, 20))
                .getFirst()
                .notes();
        assertThat(notes.getFirst().position().newLine()).isEqualTo(2);
        assertThat(notes.getFirst().position().lineRange().start().lineCode()).isEqualTo("hash_0_2");
        assertThat(notes.getLast().position()).isNull();
        server.verify();
    }

    @Test
    void rawFileUsesEncodedPathAndFullShaAndRedactsCredentials() {
        server.expect(requestTo(
                        "https://gitlab.example/api/v4/projects/group%2Frepo/repository/files/src%2Fsome%20file%23.java/raw?ref="
                                + SHA))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("first\npassword=example-value\nlast\n", MediaType.TEXT_PLAIN));
        assertThat(gateway.getRepositoryFile("group/repo", "src/some file#.java", SHA))
                .isEqualTo("first\npassword=[REDACTED]\nlast\n");
        server.verify();
    }

    @Test
    void honorsConfiguredDownloadLimitForContextAndDoesNotReturnTruncatedJson() {
        configure(10, 100);
        expect(MR_URL + "/discussions/thread", discussion());
        assertThatThrownBy(() -> gateway.getMergeRequestDiscussion("group/repo", "17", "thread"))
                .isInstanceOf(GitlabDownloadLimitException.class);
        server.verify();
    }

    @Test
    void rawFileHasAdditionalOneMegabyteLimit() {
        server.expect(requestTo(
                        "https://gitlab.example/api/v4/projects/group%2Frepo/repository/files/a.txt/raw?ref=" + SHA))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("x".repeat(1_000_001), MediaType.TEXT_PLAIN));
        assertThatThrownBy(() -> gateway.getRepositoryFile("group/repo", "a.txt", SHA))
                .isInstanceOf(GitlabDownloadLimitException.class);
        server.verify();
    }

    @Test
    void boundsPageSizeAndRejectsMutableRefsOrPathInjectionBeforeHttp() {
        configure(100_000_000, 2);
        expect(MR_URL + "/versions?page=3&per_page=2", "[]");
        assertThat(gateway.listMergeRequestDiffVersions("group/repo", "17", new GitlabPageRequest(3, 20)))
                .isEmpty();
        assertThatThrownBy(() -> gateway.getRepositoryFile("group/repo", "a.txt", "main"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.getMergeRequestDiscussion("group/repo", "17", "../../notes"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.getRepositoryFile("other/repo", "a.txt", SHA))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void downloadLimitDuringFallbackBecomesExplicitContextStatus() {
        expect(MR_URL + "/discussions/thread", discussion());
        expect(MR_URL + "/versions?page=1&per_page=20", "[]");
        server.expect(requestTo(
                        "https://gitlab.example/api/v4/projects/group%2Frepo/repository/files/new.java/raw?ref=" + SHA))
                .andRespond(withSuccess("x".repeat(1_000_001), MediaType.TEXT_PLAIN));
        var result = new DiscussionContextService(gateway).get("group/repo", "17", "thread", 0);
        assertThat(result.context().status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(result.context().reason()).contains("download limit");
        server.verify();
    }

    private void configure(long maxBytes, int maxPerPage) {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new GitlabProperties(
                "https://gitlab.example",
                "test-token",
                List.of("group/repo"),
                20,
                maxPerPage,
                500,
                20,
                3,
                null,
                Duration.ofSeconds(10),
                Duration.ofSeconds(60),
                null,
                null,
                null,
                maxBytes,
                0,
                Duration.ZERO);
        gateway = new RestGitlabGateway(new GitlabApiClient(properties, mapper, builder));
    }

    private void expect(String url, String json) {
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private String discussion() {
        // GitLab 15.1 Discussions API shape: line_range endpoints may contain only type and line_code.
        return """
                {"id":"thread","individual_note":false,"notes":[
                  {"id":1,"type":"DiffNote","body":"Please fix","resolved":false,
                   "position":{"base_sha":"%s","start_sha":"%s","head_sha":"%s","position_type":"text",
                     "old_path":"old.java","new_path":"new.java","new_line":2,
                     "line_range":{"start":{"type":"new","line_code":"hash_0_2"},"end":{"type":"new","line_code":"hash_0_2"}}}},
                  {"id":2,"type":"DiscussionNote","body":"Reply","unknown_future_field":true}]}
                """.formatted(SHA, SHA, SHA);
    }

    private String version(boolean withDiff) {
        String diffs = withDiff
                ? ",\"diffs\":[{\"old_path\":\"old.java\",\"new_path\":\"new.java\",\"renamed_file\":true,\"diff\":\"@@ -1 +1,2 @@\\n before\\n+new code\\n\"}]"
                : "";
        return """
                {"id":8,"base_commit_sha":"%s","start_commit_sha":"%s","head_commit_sha":"%s","state":"collected"%s}
                """.formatted(SHA, SHA, SHA, diffs);
    }
}
