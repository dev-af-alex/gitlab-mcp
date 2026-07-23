package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class GitlabTokenProvider {

    private final GitlabProperties properties;

    public GitlabTokenProvider(GitlabProperties properties) {
        this.properties = properties;
    }

    public String get() {
        if (StringUtils.hasText(properties.token())) {
            return properties.token().trim();
        }
        if (StringUtils.hasText(properties.tokenFile())) {
            try {
                String token = Files.readString(Path.of(properties.tokenFile().trim())).trim();
                if (StringUtils.hasText(token)) {
                    return token;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Unable to read GitLab token from GITLAB_TOKEN_FILE", e);
            }
        }
        throw new IllegalArgumentException("GITLAB_TOKEN or GITLAB_TOKEN_FILE must be set");
    }
}
