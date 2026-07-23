package com.alexaf.gitlabmcp.gitlab.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "gitlab")
public record GitlabProperties(
        String url,
        String token,
        List<String> allowedProjects,
        int defaultPerPage,
        int maxPerPage,
        int maxJobs,
        int maxPipelines,
        int maxPipelineDepth,
        String tokenFile,
        Duration connectTimeout,
        Duration readTimeout,
        String proxyUrl,
        String sslBundle,
        String tempDirectory,
        long maxDownloadBytes,
        int retryAttempts,
        Duration retryBackoff
) {

    public GitlabProperties(
            String url,
            String token,
            List<String> allowedProjects,
            int defaultPerPage,
            int maxPerPage
    ) {
        this(
                url,
                token,
                allowedProjects,
                defaultPerPage,
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
                100_000_000L,
                0,
                Duration.ZERO);
    }
}
