package com.alexaf.gitlabmcp.domain;

import java.util.List;

public record PipelineAnalysis(List<Finding> findings, List<String> analyzers) {

    public PipelineAnalysis {
        findings = List.copyOf(findings);
        analyzers = List.copyOf(analyzers);
    }
}
