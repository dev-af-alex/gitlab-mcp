package com.alexaf.gitlabmcp.adapter.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.alexaf.gitlabmcp.domain.Confidence;
import com.alexaf.gitlabmcp.domain.Evidence;
import com.alexaf.gitlabmcp.domain.Finding;
import com.alexaf.gitlabmcp.domain.FindingCategory;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.port.FailureAnalyzer;

public abstract class TracePatternFailureAnalyzer implements FailureAnalyzer {

    private final String id;
    private final String toolchain;
    private final int priority;
    private final List<Pattern> patterns;
    private final String recommendation;

    protected TracePatternFailureAnalyzer(
            String id, String toolchain, int priority, List<String> patterns, String recommendation) {
        this.id = id;
        this.toolchain = toolchain;
        this.priority = priority;
        this.patterns = patterns.stream()
                .map(pattern -> Pattern.compile(pattern, Pattern.CASE_INSENSITIVE))
                .toList();
        this.recommendation = recommendation;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public boolean supports(PipelineContext context) {
        return context.buildSignals().contains(toolchain) && !context.traces().isEmpty();
    }

    @Override
    public List<Finding> analyze(PipelineContext context) {
        List<Finding> findings = new ArrayList<>();
        for (Job job : context.jobs()) {
            String trace = context.traces().get(job.id());
            if (!"failed".equals(job.status()) || trace == null) {
                continue;
            }
            List<Evidence> evidence = trace.lines()
                    .map(String::strip)
                    .filter(this::matches)
                    .distinct()
                    .limit(10)
                    .map(line -> new Evidence("job-trace", job.id(), null, line))
                    .toList();
            if (evidence.isEmpty()) {
                continue;
            }
            findings.add(new Finding(
                    category(evidence),
                    toolchain,
                    Confidence.HIGH,
                    job.name() + ": " + evidence.getFirst().message(),
                    evidence,
                    List.of(recommendation)));
        }
        return List.copyOf(findings);
    }

    private boolean matches(String line) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(line).find());
    }

    private FindingCategory category(List<Evidence> evidence) {
        String text = evidence.stream()
                .map(Evidence::message)
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase(Locale.ROOT);
        return text.contains("test") || text.contains("fail") || text.contains("assert")
                ? FindingCategory.TEST
                : FindingCategory.BUILD;
    }
}
