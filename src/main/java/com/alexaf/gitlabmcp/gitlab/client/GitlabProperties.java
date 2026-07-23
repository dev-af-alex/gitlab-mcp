package com.alexaf.gitlabmcp.gitlab.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gitlab")
public record GitlabProperties(
        String url,
        String token,
        List<String> allowedProjects,
        int defaultPerPage,
        int maxPerPage,
        int maxJobs
) {

    public GitlabProperties(
            String url,
            String token,
            List<String> allowedProjects,
            int defaultPerPage,
            int maxPerPage
    ) {
        this(url, token, allowedProjects, defaultPerPage, maxPerPage, 500);
    }
}
