package com.alexaf.gitlabmcp.adapter.analysis.python;

import java.util.List;

import org.springframework.stereotype.Component;

import com.alexaf.gitlabmcp.adapter.analysis.TracePatternFailureAnalyzer;

@Component
public class PytestTraceFailureAnalyzer extends TracePatternFailureAnalyzer {

    public PytestTraceFailureAnalyzer() {
        super(
                "pytest-trace",
                "python",
                170,
                List.of("^FAILED\\s+\\S+::", "^ERROR\\s+\\S+::", "AssertionError", "short test summary info"),
                "Inspect the failing pytest node and its assertion or fixture setup.");
    }
}
