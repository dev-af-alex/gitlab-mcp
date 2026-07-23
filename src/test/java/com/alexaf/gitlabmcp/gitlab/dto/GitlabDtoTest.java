package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitlabDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void jobDeserializesGitlabFieldsAndArtifacts() throws Exception {
        String json = """
                {
                  "id": 8,
                  "name": "test",
                  "stage": "verify",
                  "status": "failed",
                  "failure_reason": "script_failure",
                  "web_url": "https://gitlab.example/group/repo/-/jobs/8",
                  "ref": "main",
                  "tag": false,
                  "allow_failure": false,
                  "created_at": "2026-07-08T10:00:00.000Z",
                  "started_at": "2026-07-08T10:01:00.000Z",
                  "finished_at": "2026-07-08T10:02:00.000Z",
                  "duration": 60.5,
                  "queued_duration": 2.25,
                  "artifacts": [
                    {
                      "file_type": "junit",
                      "size": 1234,
                      "filename": "junit.xml",
                      "file_format": "gzip"
                    }
                  ],
                  "unknown": "ignored"
                }
                """;

        Job job = objectMapper.readValue(json, Job.class);

        assertThat(job.id()).isEqualTo(8L);
        assertThat(job.name()).isEqualTo("test");
        assertThat(job.stage()).isEqualTo("verify");
        assertThat(job.status()).isEqualTo("failed");
        assertThat(job.failureReason()).isEqualTo("script_failure");
        assertThat(job.webUrl()).isEqualTo("https://gitlab.example/group/repo/-/jobs/8");
        assertThat(job.allowFailure()).isFalse();
        assertThat(job.duration()).isEqualTo(60.5);
        assertThat(job.queuedDuration()).isEqualTo(2.25);
        assertThat(job.artifacts()).containsExactly(new JobArtifact("junit", 1234L, "junit.xml", "gzip"));
    }

    @Test
    void artifactFileDeserializesArtifactTreeEntry() throws Exception {
        String json = """
                {
                  "name": "TEST-ServiceTest.xml",
                  "path": "target/surefire-reports/TEST-ServiceTest.xml",
                  "type": "file",
                  "size": 987,
                  "mode": "100644",
                  "unknown": "ignored"
                }
                """;

        ArtifactFile file = objectMapper.readValue(json, ArtifactFile.class);

        assertThat(file).isEqualTo(new ArtifactFile(
                "TEST-ServiceTest.xml",
                "target/surefire-reports/TEST-ServiceTest.xml",
                "file",
                987L,
                "100644"));
    }
}
