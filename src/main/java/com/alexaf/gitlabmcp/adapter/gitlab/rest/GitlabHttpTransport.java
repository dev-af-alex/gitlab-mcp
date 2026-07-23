package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabApiException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabClientException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabForbiddenException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabNotFoundException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabRateLimitedException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabServerException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabTransportException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabUnauthorizedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;

@Component
public class GitlabHttpTransport {

    private final GitlabProperties properties;
    private final RestClient restClient;
    private final String apiUrl;

    public GitlabHttpTransport(GitlabProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.apiUrl = normalizeApiUrl(properties.url());
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    public String getText(String path, List<GitlabQueryParameter> queryParameters) {
        URI uri = uri(path, queryParameters);
        try {
            return restClient.get()
                    .uri(uri)
                    .header("PRIVATE-TOKEN", requireToken(properties.token()))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw exception(uri, e.getStatusCode(), e.getResponseHeaders(), e);
        } catch (RestClientException e) {
            throw new GitlabTransportException(uri, e);
        }
    }

    public Path download(String path, List<GitlabQueryParameter> queryParameters) {
        URI uri = uri(path, queryParameters);
        try {
            return restClient.get()
                    .uri(uri)
                    .header("PRIVATE-TOKEN", requireToken(properties.token()))
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.isError()) {
                            throw exception(uri, status, response.getHeaders(), null);
                        }
                        Path file = Files.createTempFile("gitlab-mcp-", ".tmp");
                        try (var input = response.getBody();
                             var output = Files.newOutputStream(file, StandardOpenOption.WRITE)) {
                            input.transferTo(output);
                            return file;
                        } catch (Exception e) {
                            delete(file);
                            throw new GitlabTransportException(uri, e);
                        }
                    });
        } catch (GitlabApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GitlabTransportException(uri, e);
        }
    }

    private URI uri(String path, List<GitlabQueryParameter> queryParameters) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(apiUrl + path);
        for (GitlabQueryParameter queryParameter : queryParameters) {
            if (queryParameter.value() != null
                    && StringUtils.hasText(queryParameter.value().toString())) {
                builder.queryParam(queryParameter.name(), queryParameter.value());
            }
        }
        return builder.build(true).toUri();
    }

    private GitlabApiException exception(
            URI uri,
            HttpStatusCode status,
            HttpHeaders headers,
            Throwable cause
    ) {
        return switch (status.value()) {
            case 401 -> new GitlabUnauthorizedException(uri, cause);
            case 403 -> new GitlabForbiddenException(uri, cause);
            case 404 -> new GitlabNotFoundException(uri, cause);
            case 429 -> new GitlabRateLimitedException(uri, retryAfter(headers), cause);
            default -> status.is5xxServerError()
                    ? new GitlabServerException(uri, status.value(), cause)
                    : new GitlabClientException(uri, status.value(), cause);
        };
    }

    private Duration retryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeApiUrl(String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("GITLAB_URL must be set");
        }
        String trimmed = trimTrailingSlash(url.trim());
        return trimmed.endsWith("/api/v4") ? trimmed : trimmed + "/api/v4";
    }

    private String requireToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("GITLAB_TOKEN must be set");
        }
        return token.trim();
    }

    private String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
        }
    }
}
