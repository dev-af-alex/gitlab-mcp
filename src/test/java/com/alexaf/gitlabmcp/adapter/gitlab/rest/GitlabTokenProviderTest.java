package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitlabTokenProviderTest {

    @TempDir
    Path tempDirectory;

    @Test
    void readsTokenFromFileWhenEnvironmentValueIsEmpty() throws Exception {
        Path tokenFile = tempDirectory.resolve("gitlab-token");
        Files.writeString(tokenFile, " file-token \n");
        GitlabProperties properties = new GitlabProperties(
                "https://gitlab.example",
                "",
                List.of(),
                20,
                100,
                500,
                20,
                3,
                tokenFile.toString(),
                Duration.ofSeconds(10),
                Duration.ofSeconds(60),
                null,
                null,
                tempDirectory.toString(),
                1_000_000,
                0,
                Duration.ZERO);

        assertThat(new GitlabTokenProvider(properties).get()).isEqualTo("file-token");
    }
}
