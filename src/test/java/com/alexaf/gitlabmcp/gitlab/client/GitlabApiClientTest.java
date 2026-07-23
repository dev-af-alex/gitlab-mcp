package com.alexaf.gitlabmcp.gitlab.client;

import com.alexaf.gitlabmcp.gitlab.client.error.GitlabApiException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabForbiddenException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabNotFoundException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabRateLimitedException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabServerException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabUnauthorizedException;
import com.alexaf.gitlabmcp.gitlab.dto.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GitlabApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static Stream<Arguments> projectPathInputs() {
        return Stream.of(
                Arguments.of("123", "123"),
                Arguments.of("project #123", "123"),
                Arguments.of(" group / subgroup / repo.git ", "group%2Fsubgroup%2Frepo"),
                Arguments.of("git@gitlab.example:group/subgroup/repo.git", "group%2Fsubgroup%2Frepo"),
                Arguments.of("https://gitlab.example/group/subgroup/repo/-/merge_requests/42", "group%2Fsubgroup%2Frepo"),
                Arguments.of("https://gitlab.example/api/v4/projects/group%2Fsubgroup%2Frepo", "group%2Fsubgroup%2Frepo"),
                Arguments.of("\"group/subgroup/repo!42\"", "group%2Fsubgroup%2Frepo")
        );
    }

    private static Stream<Arguments> mergeRequestIidInputs() {
        return Stream.of(
                Arguments.of("2115", 2115L),
                Arguments.of("!2115", 2115L),
                Arguments.of("group/repo!2115", 2115L),
                Arguments.of("https://gitlab.example/group/repo/-/merge_requests/2115", 2115L)
        );
    }

    private static Stream<Arguments> pipelineIdInputs() {
        return Stream.of(
                Arguments.of("123", 123L),
                Arguments.of("#123", 123L),
                Arguments.of("pipeline #123", 123L),
                Arguments.of("https://gitlab.example/group/repo/-/pipelines/123", 123L),
                Arguments.of("https://gitlab.example/api/v4/projects/group%2Frepo/pipelines/123", 123L)
        );
    }

    private static Stream<Arguments> jobIdInputs() {
        return Stream.of(
                Arguments.of("8", 8L),
                Arguments.of("#8", 8L),
                Arguments.of("job #8", 8L),
                Arguments.of("https://gitlab.example/group/repo/-/jobs/8", 8L),
                Arguments.of("https://gitlab.example/api/v4/projects/group%2Frepo/jobs/8", 8L)
        );
    }

    private static Stream<Arguments> mergeRequestStates() {
        return Stream.of(
                Arguments.of(null, "opened"),
                Arguments.of("open", "opened"),
                Arguments.of("CLOSED", "closed"),
                Arguments.of("locked", "locked"),
                Arguments.of("merge", "merged"),
                Arguments.of("any", "all"),
                Arguments.of("*", "all")
        );
    }

    private static byte[] zip(String... entries) throws Exception {
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

    @ParameterizedTest
    @MethodSource("projectPathInputs")
    void projectPathNormalizesAndEncodesSupportedProjectIdentifiers(String input, String expected) {
        GitlabApiClient client = client();

        assertThat(client.projectPath(input)).isEqualTo(expected);
    }

    @Test
    void projectPathRejectsProjectOutsideAllowListAfterNormalization() {
        GitlabApiClient client = client(List.of("Group/Subgroup/Repo", "123"));

        assertThat(client.projectPath("https://gitlab.example/group/subgroup/repo/-/merge_requests/42"))
                .isEqualTo("group%2Fsubgroup%2Frepo");
        assertThat(client.projectPath("project #123")).isEqualTo("123");
        assertThatThrownBy(() -> client.projectPath("group/other/repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project is not allowed by GITLAB_ALLOWED_PROJECTS");
    }

    @ParameterizedTest
    @MethodSource("mergeRequestIidInputs")
    void mergeRequestIidAcceptsNumericReferenceAndUrlForms(String input, long expected) {
        GitlabApiClient client = client();

        assertThat(client.mergeRequestIid(input)).isEqualTo(expected);
    }

    @Test
    void mergeRequestIidRejectsUnsupportedValues() {
        GitlabApiClient client = client();

        assertThatThrownBy(() -> client.mergeRequestIid("not-an-iid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mergeRequestIid must be a numeric IID");
    }

    @ParameterizedTest
    @MethodSource("pipelineIdInputs")
    void pipelineIdAcceptsNumericReferenceAndUrlForms(String input, long expected) {
        GitlabApiClient client = client();

        assertThat(client.pipelineId(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("jobIdInputs")
    void jobIdAcceptsNumericReferenceAndUrlForms(String input, long expected) {
        GitlabApiClient client = client();

        assertThat(client.jobId(input)).isEqualTo(expected);
    }

    @Test
    void pipelineIdAndJobIdRejectUnsupportedValues() {
        GitlabApiClient client = client();

        assertThatThrownBy(() -> client.pipelineId("not-a-pipeline"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pipelineId must be a numeric id or GitLab URL");
        assertThatThrownBy(() -> client.jobId("not-a-job"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobId must be a numeric id or GitLab URL");
    }

    @ParameterizedTest
    @MethodSource("mergeRequestStates")
    void mergeRequestStateNormalizesSupportedAliases(String input, String expected) {
        GitlabApiClient client = client();

        assertThat(client.mergeRequestState(input)).isEqualTo(expected);
    }

    @Test
    void mergeRequestStateRejectsUnsupportedState() {
        GitlabApiClient client = client();

        assertThatThrownBy(() -> client.mergeRequestState("review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported merge request state");
    }

    @Test
    void pageAndPerPageApplyDefaultsAndBounds() {
        GitlabApiClient client = client(new GitlabProperties("https://gitlab.example", "token", List.of(), 20, 50));

        assertThat(client.page(null)).isEqualTo(1);
        assertThat(client.page(0)).isEqualTo(1);
        assertThat(client.page(3)).isEqualTo(3);
        assertThat(client.perPage(null)).isEqualTo(20);
        assertThat(client.perPage(0)).isEqualTo(20);
        assertThat(client.perPage(30)).isEqualTo(30);
        assertThat(client.perPage(100)).isEqualTo(50);
    }

    @Test
    void getRequiresTokenBeforeRequestingGitlab() {
        GitlabApiClient client = client(new GitlabProperties("https://gitlab.example", "", List.of(), 20, 100));

        assertThatThrownBy(() -> client.get("/user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("GITLAB_TOKEN must be set");
    }

    @Test
    void getBuildsApiUriAddsPrivateTokenAndPrettyPrintsJson() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitlabApiClient client = client(
                new GitlabProperties("https://gitlab.example/", " token ", List.of(), 20, 100),
                builder);

        server.expect(once(), requestTo("https://gitlab.example/api/v4/projects?search=demo&membership=true&page=2&per_page=10"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("PRIVATE-TOKEN", "token"))
                .andRespond(withSuccess("{\"id\":7,\"name\":\"Demo\"}", MediaType.APPLICATION_JSON));

        String response = client.get("/projects",
                client.param("search", "demo"),
                client.param("membership", true),
                client.param("page", 2),
                client.param("per_page", 10),
                client.param("ignored", ""));

        assertThat(objectMapper.readTree(response).path("id").asLong()).isEqualTo(7L);
        assertThat(response).contains(System.lineSeparator());
        server.verify();
    }

    @Test
    void getRawTextReturnsTraceWithoutJsonPrettyPrinting() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitlabApiClient client = client(
                new GitlabProperties("https://gitlab.example/", "token", List.of(), 20, 100),
                builder);

        server.expect(once(), requestTo("https://gitlab.example/api/v4/projects/1/jobs/8/trace"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("PRIVATE-TOKEN", "token"))
                .andRespond(withSuccess("{not-json}\nline 2", MediaType.TEXT_PLAIN));

        assertThat(client.getRawText("/projects/1/jobs/8/trace")).isEqualTo("{not-json}\nline 2");
        server.verify();
    }

    @Test
    void getAllPagesFollowsGitlabLinkHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitlabApiClient client = client(
                new GitlabProperties("https://gitlab.example/", "token", List.of(), 20, 100),
                builder);
        String nextPage = "https://gitlab.example/api/v4/projects?page=2&per_page=1";

        server.expect(once(), requestTo("https://gitlab.example/api/v4/projects?page=1&per_page=1"))
                .andRespond(withSuccess("[{\"id\":1,\"name\":\"one\"}]", MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<" + nextPage + ">; rel=\"next\""));
        server.expect(once(), requestTo(nextPage))
                .andRespond(withSuccess("[{\"id\":2,\"name\":\"two\"}]", MediaType.APPLICATION_JSON));

        var page = client.getAllPages(
                "/projects",
                Project.class,
                10,
                client.param("page", 1),
                client.param("per_page", 1));

        assertThat(page.items()).extracting(Project::id).containsExactly(1L, 2L);
        assertThat(page.totalFetched()).isEqualTo(2);
        assertThat(page.nextLink()).isNull();
        assertThat(page.truncated()).isFalse();
        server.verify();
    }

    @Test
    void getAllPagesStopsAtConfiguredItemLimit() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitlabApiClient client = client(
                new GitlabProperties("https://gitlab.example/", "token", List.of(), 20, 100),
                builder);

        server.expect(once(), requestTo("https://gitlab.example/api/v4/projects?per_page=100"))
                .andRespond(withSuccess("""
                        [{"id": 1, "name": "one"}, {"id": 2, "name": "two"}]
                        """, MediaType.APPLICATION_JSON));

        var page = client.getAllPages(
                "/projects",
                Project.class,
                1,
                client.param("per_page", 100));

        assertThat(page.items()).extracting(Project::id).containsExactly(1L);
        assertThat(page.totalFetched()).isEqualTo(1);
        assertThat(page.truncated()).isTrue();
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("gitlabErrorStatuses")
    void getMapsHttpStatusesToTypedExceptions(
            HttpStatus status,
            Class<? extends GitlabApiException> exceptionType
    ) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitlabApiClient client = client(
                new GitlabProperties("https://gitlab.example/", "token", List.of(), 20, 100),
                builder);

        server.expect(once(), requestTo("https://gitlab.example/api/v4/user"))
                .andRespond(withStatus(status).header("Retry-After", "7"));

        assertThatThrownBy(() -> client.get("/user"))
                .isInstanceOf(exceptionType)
                .satisfies(error -> assertThat(((GitlabApiException) error).statusCode())
                        .isEqualTo(status.value()));
        server.verify();
    }

    @Test
    void rateLimitExceptionExposesRetryDelay() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitlabApiClient client = client(
                new GitlabProperties("https://gitlab.example/", "token", List.of(), 20, 100),
                builder);

        server.expect(once(), requestTo("https://gitlab.example/api/v4/user"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "7"));

        assertThatThrownBy(() -> client.get("/user"))
                .isInstanceOf(GitlabRateLimitedException.class)
                .satisfies(error -> assertThat(((GitlabRateLimitedException) error).retryAfter())
                        .hasSeconds(7));
    }

    private static Stream<Arguments> gitlabErrorStatuses() {
        return Stream.of(
                Arguments.of(HttpStatus.UNAUTHORIZED, GitlabUnauthorizedException.class),
                Arguments.of(HttpStatus.FORBIDDEN, GitlabForbiddenException.class),
                Arguments.of(HttpStatus.NOT_FOUND, GitlabNotFoundException.class),
                Arguments.of(HttpStatus.TOO_MANY_REQUESTS, GitlabRateLimitedException.class),
                Arguments.of(HttpStatus.BAD_GATEWAY, GitlabServerException.class)
        );
    }

    @Test
    void getLimitedTextRedactsSecretsAndTruncatesByUtf8Bytes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitlabApiClient client = client(
                new GitlabProperties("https://gitlab.example/", "token", List.of(), 20, 100),
                builder);

        server.expect(once(), requestTo("https://gitlab.example/api/v4/projects/1/jobs/8/artifacts/target/report.txt"))
                .andRespond(withSuccess("GITLAB_TOKEN=secret-token\n0123456789", MediaType.TEXT_PLAIN));

        String response = client.getLimitedText("/projects/1/jobs/8/artifacts/target/report.txt", 24);

        assertThat(response).isEqualTo("GITLAB_TOKEN=[REDACTED]\n[truncated to 24 bytes]");
        server.verify();
    }

    @Test
    void getLimitedTextUsesDefaultBoundWhenMaxBytesIsDisabled() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitlabApiClient client = client(
                new GitlabProperties("https://gitlab.example/", "token", List.of(), 20, 100),
                builder);
        String payload = "x".repeat(60_010);

        server.expect(once(), requestTo("https://gitlab.example/api/v4/projects/1/jobs/8/artifacts/target/report.txt"))
                .andRespond(withSuccess(payload, MediaType.TEXT_PLAIN));

        String response = client.getLimitedText("/projects/1/jobs/8/artifacts/target/report.txt", 0);

        assertThat(response)
                .hasSize(60_000 + "\n[truncated to 60000 bytes]".length())
                .endsWith("[truncated to 60000 bytes]");
        server.verify();
    }

    @Test
    void getTailTextRedactsSecretsAndReturnsLastUtf8Bytes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitlabApiClient client = client(
                new GitlabProperties("https://gitlab.example/", "token", List.of(), 20, 100),
                builder);

        server.expect(once(), requestTo("https://gitlab.example/api/v4/projects/1/jobs/8/trace"))
                .andRespond(withSuccess("first line\nTOKEN=secret-token\nlast failure", MediaType.TEXT_PLAIN));

        String response = client.getTailText("/projects/1/jobs/8/trace", 32);

        assertThat(response)
                .startsWith("[truncated to last 32 bytes]\n")
                .contains("TOKEN=[REDACTED]")
                .contains("last failure")
                .doesNotContain("secret-token")
                .doesNotContain("first line");
        server.verify();
    }

    @Test
    void listArtifactArchiveStreamsZipAndAppliesPathRecursiveAndPagination() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitlabApiClient client = client(
                new GitlabProperties("https://gitlab.example/", "token", List.of(), 20, 100),
                builder);

        server.expect(once(), requestTo("https://gitlab.example/api/v4/projects/1/jobs/8/artifacts"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("PRIVATE-TOKEN", "token"))
                .andRespond(withSuccess(zip(
                        "target/surefire-reports/TEST-ServiceTest.xml",
                        "target/surefire-reports/com.example.ServiceTest.txt",
                        "target/app.jar",
                        "README.md"), MediaType.APPLICATION_OCTET_STREAM));

        var response = client.listArtifactArchive("/projects/1/jobs/8/artifacts", "target/surefire-reports", true, 1, 10);

        assertThat(response).extracting("path")
                .containsExactly(
                        "target/surefire-reports/TEST-ServiceTest.xml",
                        "target/surefire-reports/com.example.ServiceTest.txt");
        assertThat(response.getFirst().type()).isEqualTo("file");
        server.verify();
    }

    @Test
    void findArtifactArchiveFilesSupportsGlobPatterns() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitlabApiClient client = client(
                new GitlabProperties("https://gitlab.example/", "token", List.of(), 20, 100),
                builder);

        server.expect(once(), requestTo("https://gitlab.example/api/v4/projects/1/jobs/8/artifacts"))
                .andRespond(withSuccess(zip(
                        "reports/TEST-ServiceTest.xml",
                        "reports/ServiceTest.txt",
                        "logs/build.log"), MediaType.APPLICATION_OCTET_STREAM));

        var response = client.findArtifactArchiveFiles("/projects/1/jobs/8/artifacts", "**/TEST-*.xml", false, 1, 10);

        assertThat(response).singleElement()
                .satisfies(file -> assertThat(file.path()).isEqualTo("reports/TEST-ServiceTest.xml"));
        server.verify();
    }

    @Test
    void redactSecretsHandlesCommonTokenAndPasswordForms() {
        GitlabApiClient client = client();

        String redacted = client.redactSecrets("""
                PRIVATE-TOKEN: glpat-123
                password=my-password
                client_secret=secret-value
                SOME_API_KEY=abc123
                normal=value
                """);

        assertThat(redacted)
                .contains("PRIVATE-TOKEN: [REDACTED]")
                .contains("password=[REDACTED]")
                .contains("client_secret=[REDACTED]")
                .contains("SOME_API_KEY=[REDACTED]")
                .contains("normal=value")
                .doesNotContain("glpat-123")
                .doesNotContain("my-password")
                .doesNotContain("secret-value")
                .doesNotContain("abc123");
    }

    @Test
    void limitTextPreservesShortTextAndSupportsUnlimitedMode() {
        GitlabApiClient client = client();

        assertThat(client.limitText("short", 100)).isEqualTo("short");
        assertThat(client.limitText("short", null)).isEqualTo("short");
        assertThat(client.limitText("short", 0)).isEqualTo("short");
        assertThat(client.limitText(null, 100)).isEmpty();
    }

    @Test
    void tailTextPreservesShortTextAndSupportsUnlimitedMode() {
        GitlabApiClient client = client();

        assertThat(client.tailText("short", 100)).isEqualTo("short");
        assertThat(client.tailText("short", null)).isEqualTo("short");
        assertThat(client.tailText("short", 0)).isEqualTo("short");
        assertThat(client.tailText(null, 100)).isEmpty();
        assertThat(client.tailText("0123456789", 4)).isEqualTo("[truncated to last 4 bytes]\n6789");
    }

    private GitlabApiClient client() {
        return client(List.of());
    }

    private GitlabApiClient client(List<String> allowedProjects) {
        return client(new GitlabProperties("https://gitlab.example", "token", allowedProjects, 20, 100));
    }

    private GitlabApiClient client(GitlabProperties properties) {
        return client(properties, RestClient.builder());
    }

    private GitlabApiClient client(GitlabProperties properties, RestClient.Builder builder) {
        return new GitlabApiClient(properties, objectMapper, builder);
    }
}
