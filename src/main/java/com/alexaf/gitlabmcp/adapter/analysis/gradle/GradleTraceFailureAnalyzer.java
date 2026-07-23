package com.alexaf.gitlabmcp.adapter.analysis.gradle;

import com.alexaf.gitlabmcp.adapter.analysis.TracePatternFailureAnalyzer;
import org.springframework.stereotype.Component;

import java.util.List;

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
