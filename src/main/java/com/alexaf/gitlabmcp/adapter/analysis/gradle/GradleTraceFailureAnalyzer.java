package com.alexaf.gitlabmcp.adapter.analysis.gradle;

import java.util.List;

import org.springframework.stereotype.Component;

import com.alexaf.gitlabmcp.adapter.analysis.TracePatternFailureAnalyzer;

@Component
public class GradleTraceFailureAnalyzer extends TracePatternFailureAnalyzer {

    public GradleTraceFailureAnalyzer() {
        super(
                "gradle-trace",
                "gradle",
                180,
                List.of(
                        "^Execution failed for task",
                        "^FAILURE: Build failed",
                        "There were failing tests",
                        "\\bFAILED\\b"),
                "Inspect the failed Gradle task and its generated test report.");
    }
}
