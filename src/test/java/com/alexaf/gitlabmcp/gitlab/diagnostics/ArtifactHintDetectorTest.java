package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.JobArtifact;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactHintDetectorTest {

    private final ArtifactHintDetector detector = new ArtifactHintDetector();

    @Test
    void usefulArtifactsKeepsTestAndCoverageArtifactsOnly() {
        Job job = new Job(
                8L,
                "test",
                "test",
                "failed",
                null,
                null,
                "main",
                false,
                false,
                null,
                null,
                null,
                1.0,
                0.1,
                List.of(
                        new JobArtifact("junit", 100L, "junit.xml", "xml"),
                        new JobArtifact("archive", 200L, "app.jar", "zip")));
        List<ArtifactFile> files = List.of(
                new ArtifactFile(
                        "TEST-ServiceTest.xml", "target/surefire-reports/TEST-ServiceTest.xml", "file", 123L, "100644"),
                new ArtifactFile("index.html", "coverage/index.html", "file", 456L, "100644"),
                new ArtifactFile("app.jar", "target/app.jar", "file", 789L, "100644"));

        assertThat(detector.usefulArtifacts(job, files))
                .containsExactly(
                        "junit junit.xml xml", "target/surefire-reports/TEST-ServiceTest.xml", "coverage/index.html");
    }

    @Test
    void usefulArtifactsDeduplicatesHints() {
        Job job = new Job(
                8L, "test", "test", "failed", null, null, "main", false, false, null, null, null, 1.0, 0.1, List.of());
        List<ArtifactFile> files = List.of(
                new ArtifactFile("junit.xml", "target/junit.xml", "file", 123L, "100644"),
                new ArtifactFile("junit.xml", "target/junit.xml", "file", 123L, "100644"));

        assertThat(detector.usefulArtifacts(job, files)).containsExactly("target/junit.xml");
    }
}
