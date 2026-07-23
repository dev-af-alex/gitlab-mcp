package com.alexaf.gitlabmcp.gitlab.diagnostics;

import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.JobArtifact;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ArtifactHintDetector {

    private static final List<String> INTERESTING_ARTIFACT_MARKERS = List.of(
            "junit", "surefire", "failsafe", "test-results", "pytest", "allure", "eslint", "coverage"
    );

    public List<String> usefulArtifacts(Job job, List<ArtifactFile> artifactFiles) {
        List<String> result = new ArrayList<>();
        if (job.artifacts() != null) {
            job.artifacts().stream()
                    .map(this::artifactDescription)
                    .filter(this::interestingArtifact)
                    .forEach(result::add);
        }
        artifactFiles.stream()
                .map(ArtifactFile::path)
                .filter(this::interestingArtifact)
                .forEach(result::add);
        return result.stream().distinct().toList();
    }

    private String artifactDescription(JobArtifact artifact) {
        return String.join(" ",
                nullToEmpty(artifact.fileType()),
                nullToEmpty(artifact.filename()),
                nullToEmpty(artifact.fileFormat())).strip();
    }

    private boolean interestingArtifact(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return INTERESTING_ARTIFACT_MARKERS.stream().anyMatch(normalized::contains);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
