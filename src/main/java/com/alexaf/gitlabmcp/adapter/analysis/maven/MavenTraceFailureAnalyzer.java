package com.alexaf.gitlabmcp.adapter.analysis.maven;

import com.alexaf.gitlabmcp.domain.Confidence;
import com.alexaf.gitlabmcp.domain.Evidence;
import com.alexaf.gitlabmcp.domain.Finding;
import com.alexaf.gitlabmcp.domain.FindingCategory;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.gitlab.diagnostics.MavenFailureAnalyzer;
import com.alexaf.gitlabmcp.gitlab.diagnostics.MavenFailureSummary;
import com.alexaf.gitlabmcp.port.FailureAnalyzer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MavenTraceFailureAnalyzer implements FailureAnalyzer {

    private final MavenFailureAnalyzer mavenFailureAnalyzer;

    public MavenTraceFailureAnalyzer(MavenFailureAnalyzer mavenFailureAnalyzer) {
        this.mavenFailureAnalyzer = mavenFailureAnalyzer;
    }

    @Override
    public String id() {
        return "maven-trace";
    }

    @Override
    public int priority() {
        return 200;
    }

    @Override
    public boolean supports(PipelineContext context) {
        return context.traces().values().stream().anyMatch(this::looksLikeMaven);
    }

    @Override
    public List<Finding> analyze(PipelineContext context) {
        List<Finding> findings = new ArrayList<>();
        context.traces().forEach((jobId, trace) -> {
            MavenFailureSummary summary = mavenFailureAnalyzer.analyze(trace);
            if (!summary.mavenDetected()) {
                return;
            }
            FindingCategory category = summary.compilationFailureDetected()
                    || summary.executionFailureDetected()
                    ? FindingCategory.BUILD
                    : FindingCategory.TEST;
            List<Evidence> evidence = summary.evidence().stream()
                    .limit(10)
                    .map(message -> new Evidence("job-trace", jobId, null, message))
                    .toList();
            findings.add(new Finding(
                    category,
                    "maven",
                    confidence(summary.confidence()),
                    summary.detectedCause(),
                    evidence,
                    List.of("Inspect Maven diagnostics and the relevant Surefire report.")));
        });
        return List.copyOf(findings);
    }

    private boolean looksLikeMaven(String trace) {
        if (trace == null) {
            return false;
        }
        String normalized = trace.toLowerCase(Locale.ROOT);
        return normalized.contains("mvn ")
                || normalized.contains("maven-")
                || normalized.contains("surefire")
                || normalized.contains("[info] build");
    }

    private Confidence confidence(String value) {
        if ("high".equalsIgnoreCase(value)) {
            return Confidence.HIGH;
        }
        if ("medium".equalsIgnoreCase(value)) {
            return Confidence.MEDIUM;
        }
        return Confidence.LOW;
    }
}
