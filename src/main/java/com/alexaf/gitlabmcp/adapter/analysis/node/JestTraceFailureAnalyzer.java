package com.alexaf.gitlabmcp.adapter.analysis.node;

import java.util.List;

import org.springframework.stereotype.Component;

import com.alexaf.gitlabmcp.adapter.analysis.TracePatternFailureAnalyzer;

@Component
public class JestTraceFailureAnalyzer extends TracePatternFailureAnalyzer {

    public JestTraceFailureAnalyzer() {
        super(
                "jest-trace",
                "node",
                170,
                List.of("^FAIL\\s+\\S+", "^\\s*●\\s+", "^Test Suites:.*failed", "^Tests:.*failed"),
                "Inspect the failing Jest suite and compare expected and received values.");
    }
}
