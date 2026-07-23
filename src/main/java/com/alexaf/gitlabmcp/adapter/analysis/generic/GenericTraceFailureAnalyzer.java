package com.alexaf.gitlabmcp.adapter.analysis.generic;

import com.alexaf.gitlabmcp.domain.Confidence;
import com.alexaf.gitlabmcp.domain.Evidence;
import com.alexaf.gitlabmcp.domain.Finding;
import com.alexaf.gitlabmcp.domain.FindingCategory;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.gitlab.diagnostics.TraceAnalysis;
import com.alexaf.gitlabmcp.gitlab.diagnostics.TraceAnalyzer;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.port.FailureAnalyzer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GenericTraceFailureAnalyzer implements FailureAnalyzer {

    private final TraceAnalyzer traceAnalyzer;

    public GenericTraceFailureAnalyzer(TraceAnalyzer traceAnalyzer) {
        this.traceAnalyzer = traceAnalyzer;
    }

    @Override
    public String id() {
        return "generic-trace";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean supports(PipelineContext context) {
        return !context.traces().isEmpty();
    }

    @Override
    public List<Finding> analyze(PipelineContext context) {
        List<Finding> findings = new ArrayList<>();
        for (Job job : context.jobs()) {
            String trace = context.traces().get(job.id());
            if (trace == null || !"failed".equals(job.status())) {
                continue;
            }
            TraceAnalysis analysis = traceAnalyzer.analyze(job, trace);
            List<Evidence> evidence = analysis.evidence().stream()
                    .limit(10)
                    .map(message -> new Evidence("job-trace", job.id(), null, message))
                    .toList();
            findings.add(new Finding(
                    category(analysis.detectedCause()),
                    "generic",
                    confidence(analysis.confidence()),
                    job.name() + ": " + analysis.detectedCause(),
                    evidence,
                    analysis.nextSteps()));
        }
        return List.copyOf(findings);
    }

    private FindingCategory category(String cause) {
        String value = cause == null ? "" : cause.toLowerCase();
        if (value.contains("test")) {
            return FindingCategory.TEST;
        }
        if (value.contains("permission") || value.contains("authentication")) {
            return FindingCategory.SECURITY;
        }
        if (value.contains("memory") || value.contains("timeout")) {
            return FindingCategory.INFRASTRUCTURE;
        }
        if (value.contains("build") || value.contains("compilation")) {
            return FindingCategory.BUILD;
        }
        return FindingCategory.UNKNOWN;
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
