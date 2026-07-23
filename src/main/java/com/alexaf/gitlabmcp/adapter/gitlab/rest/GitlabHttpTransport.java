package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabApiException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabClientException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabDownloadLimitException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabForbiddenException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabNotFoundException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabRateLimitedException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabServerException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabTransportException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabUnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

@Component
public class GitlabHttpTransport {

    private final GitlabProperties properties;
    private final GitlabTokenProvider tokenProvider;
    private final RestClient restClient;
    private final String apiUrl;

    @Autowired
    public GitlabHttpTransport(
            GitlabProperties properties,
            RestClient.Builder restClientBuilder,
            GitlabTokenProvider tokenProvider
    ) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        this.apiUrl = normalizeApiUrl(properties.url());
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    public GitlabHttpTransport(GitlabProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, restClientBuilder, new GitlabTokenProvider(properties));
    }

    public String getText(String path, List<GitlabQueryParameter> queryParameters) {
        return get(path, queryParameters).body();
    }

    public GitlabHttpResponse get(String path, List<GitlabQueryParameter> queryParameters) {
        return get(uri(path, queryParameters));
    }

    public GitlabHttpResponse get(URI uri) {
        return withRetry(uri, () -> getOnce(uri));
    }

    private GitlabHttpResponse getOnce(URI uri) {
        try {
            var response = restClient.get()
                    .uri(uri)
                    .header("PRIVATE-TOKEN", tokenProvider.get())
                    .retrieve()
                    .toEntity(String.class);
            return new GitlabHttpResponse(response.getBody(), response.getHeaders());
        } catch (RestClientResponseException e) {
            throw exception(uri, e.getStatusCode(), e.getResponseHeaders(), e);
        } catch (RestClientException e) {
            throw new GitlabTransportException(uri, e);
        }
    }

    public Path download(String path, List<GitlabQueryParameter> queryParameters) {
        URI uri = uri(path, queryParameters);
        return withRetry(uri, () -> downloadOnce(uri));
    }

    private Path downloadOnce(URI uri) {
        try {
            return restClient.get()
                    .uri(uri)
                    .header("PRIVATE-TOKEN", tokenProvider.get())
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.isError()) {
                            throw exception(uri, status, response.getHeaders(), null);
                        }
                        long maxBytes = effectiveMaxDownloadBytes();
                        if (response.getHeaders().getContentLength() > maxBytes) {
                            throw new GitlabDownloadLimitException(uri, maxBytes);
                        }
                        Path file = createTempFile(uri);
                        try (var input = response.getBody();
                             var output = Files.newOutputStream(file, StandardOpenOption.WRITE)) {
                            byte[] buffer = new byte[16_384];
                            long total = 0;
                            int read;
                            while ((read = input.read(buffer)) >= 0) {
                                total += read;
                                if (total > maxBytes) {
                                    throw new GitlabDownloadLimitException(uri, maxBytes);
                                }
                                output.write(buffer, 0, read);
                            }
                            return file;
                        } catch (GitlabApiException e) {
                            delete(file);
                            throw e;
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

    private <T> T withRetry(URI uri, Supplier<T> request) {
        int attempts = Math.max(0, properties.retryAttempts());
        for (int attempt = 0; ; attempt++) {
            try {
                return request.get();
            } catch (GitlabApiException e) {
                if (attempt >= attempts || !isRetryable(e)) {
                    throw e;
                }
                pauseBeforeRetry(uri, e, attempt);
            }
        }
    }

    private boolean isRetryable(GitlabApiException error) {
        if (error instanceof GitlabRateLimitedException) {
            return true;
        }
        return error instanceof GitlabServerException
                && (error.statusCode() == 502 || error.statusCode() == 503);
    }

    private void pauseBeforeRetry(URI uri, GitlabApiException error, int attempt) {
        Duration delay = error instanceof GitlabRateLimitedException rateLimited
                && rateLimited.retryAfter() != null
                ? rateLimited.retryAfter()
                : retryBackoff(attempt);
        long delayMillis = Math.min(Math.max(0, delay.toMillis()), 30_000);
        if (delayMillis == 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitlabTransportException(uri, e);
        }
    }

    private Duration retryBackoff(int attempt) {
        Duration base = properties.retryBackoff() == null
                ? Duration.ZERO
                : properties.retryBackoff();
        long multiplier = 1L << Math.min(attempt, 10);
        return base.multipliedBy(multiplier);
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
            try {
                ZonedDateTime retryAt = ZonedDateTime.parse(
                        value.trim(),
                        DateTimeFormatter.RFC_1123_DATE_TIME);
                Duration delay = Duration.between(ZonedDateTime.now(retryAt.getZone()), retryAt);
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (Exception invalidDate) {
                return null;
            }
        }
    }

    private String normalizeApiUrl(String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("GITLAB_URL must be set");
        }
        String trimmed = trimTrailingSlash(url.trim());
        return trimmed.endsWith("/api/v4") ? trimmed : trimmed + "/api/v4";
    }

    private Path createTempFile(URI uri) {
        try {
            if (!StringUtils.hasText(properties.tempDirectory())) {
                return Files.createTempFile("gitlab-mcp-", ".tmp");
            }
            Path directory = Path.of(properties.tempDirectory().trim());
            Files.createDirectories(directory);
            return Files.createTempFile(directory, "gitlab-mcp-", ".tmp");
        } catch (Exception e) {
            throw new GitlabTransportException(uri, e);
        }
    }

    private long effectiveMaxDownloadBytes() {
        return properties.maxDownloadBytes() > 0
                ? properties.maxDownloadBytes()
                : Long.MAX_VALUE;
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
