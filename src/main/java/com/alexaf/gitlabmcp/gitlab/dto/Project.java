package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Project(
        Long id,
        String description,
        String name,
        @JsonProperty("name_with_namespace") String nameWithNamespace,
        String path,
        @JsonProperty("path_with_namespace") String pathWithNamespace,
        @JsonProperty("default_branch") String defaultBranch,
        @JsonProperty("ssh_url_to_repo") String sshUrlToRepo,
        @JsonProperty("http_url_to_repo") String httpUrlToRepo,
        @JsonProperty("web_url") String webUrl,
        @JsonProperty("readme_url") String readmeUrl,
        String visibility,
        Boolean archived,
        @JsonProperty("last_activity_at") String lastActivityAt,
        ProjectNamespace namespace) {}
