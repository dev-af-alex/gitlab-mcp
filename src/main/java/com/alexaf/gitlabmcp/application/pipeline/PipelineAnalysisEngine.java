package com.alexaf.gitlabmcp.application.pipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.alexaf.gitlabmcp.domain.Finding;
import com.alexaf.gitlabmcp.domain.PipelineAnalysis;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.port.FailureAnalyzer;

@Component
public class PipelineAnalysisEngine {

    private final List<FailureAnalyzer> analyzers;

    public PipelineAnalysisEngine(List<FailureAnalyzer> analyzers) {
        this.analyzers = analyzers.stream()
                .sorted(Comparator.comparingInt(FailureAnalyzer::priority).reversed())
                .toList();
    }

    public PipelineAnalysis analyze(PipelineContext context) {
        List<Finding> findings = new ArrayList<>();
        List<String> executedAnalyzers = new ArrayList<>();
        for (FailureAnalyzer analyzer : analyzers) {
            if (!analyzer.supports(context)) {
                continue;
            }
            executedAnalyzers.add(analyzer.id());
            findings.addAll(analyzer.analyze(context));
        }
        return new PipelineAnalysis(findings, executedAnalyzers);
    }
}
