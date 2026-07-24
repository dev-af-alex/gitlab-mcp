package com.alexaf.gitlabmcp.adapter.analysis;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.alexaf.gitlabmcp.adapter.analysis.gradle.GradleTraceFailureAnalyzer;
import com.alexaf.gitlabmcp.adapter.analysis.node.JestTraceFailureAnalyzer;
import com.alexaf.gitlabmcp.adapter.analysis.python.PytestTraceFailureAnalyzer;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.domain.PipelineGraph;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.alexaf.gitlabmcp.port.FailureAnalyzer;

import static org.assertj.core.api.Assertions.assertThat;

class TracePatternFailureAnalyzerTest {

    @Test
    void analyzesGradleFailure() {
        assertFinding(
                new GradleTraceFailureAnalyzer(),
                "gradle",
                "gradlew test\nExecution failed for task ':test'.\nThere were failing tests",
                "gradle");
    }

    @Test
    void analyzesJestFailure() {
        assertFinding(
                new JestTraceFailureAnalyzer(),
                "node",
                "npm test\nFAIL src/cart.test.ts\nTests: 1 failed, 2 passed",
                "node");
    }

    @Test
    void analyzesPytestFailure() {
        assertFinding(
                new PytestTraceFailureAnalyzer(),
                "python",
                "pytest\nFAILED tests/test_cart.py::test_total - AssertionError",
                "python");
    }

    private void assertFinding(FailureAnalyzer analyzer, String signal, String trace, String toolchain) {
        Pipeline pipeline =
                new Pipeline(42L, 1L, 11L, "abc", "main", "failed", "push", null, null, null, null, 1L, 1L, null);
        Job job = new Job(
                7L,
                "test",
                "test",
                "failed",
                "script_failure",
                null,
                "main",
                false,
                false,
                null,
                null,
                null,
                1.0,
                1.0,
                List.of());
        PipelineContext context = new PipelineContext(
                pipeline,
                List.of(job),
                Map.of(7L, trace),
                Map.of(),
                Map.of(),
                null,
                false,
                1,
                PipelineGraph.root("group/repo", pipeline),
                Set.of(signal));

        assertThat(analyzer.supports(context)).isTrue();
        assertThat(analyzer.analyze(context)).singleElement().satisfies(finding -> {
            assertThat(finding.toolchain()).isEqualTo(toolchain);
            assertThat(finding.evidence()).isNotEmpty();
        });
    }
}
