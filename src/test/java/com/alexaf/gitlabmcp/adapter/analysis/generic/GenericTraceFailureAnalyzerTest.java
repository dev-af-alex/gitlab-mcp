package com.alexaf.gitlabmcp.adapter.analysis.generic;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alexaf.gitlabmcp.domain.Confidence;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.gitlab.diagnostics.TraceAnalyzer;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;

import static org.assertj.core.api.Assertions.assertThat;

class GenericTraceFailureAnalyzerTest {

    private final GenericTraceFailureAnalyzer analyzer = new GenericTraceFailureAnalyzer(new TraceAnalyzer());

    @Test
    void fallsBackToGenericEvidenceForUnknownPythonFailure() {
        String trace = """
                =================== FAILURES ===================
                ___________________ test_checkout ___________________
                E assert response.status_code == 200
                E assert 500 == 200
                1 failed in 0.42s
                """;
        PipelineContext context = context(Map.of(7L, trace));

        assertThat(analyzer.analyze(context)).singleElement().satisfies(finding -> {
            assertThat(finding.toolchain()).isEqualTo("generic");
            assertThat(finding.confidence()).isEqualTo(Confidence.LOW);
            assertThat(finding.summary()).contains("script_failure");
            assertThat(finding.evidence())
                    .extracting(evidence -> evidence.message())
                    .anyMatch(message -> message.contains("1 failed"));
        });
    }

    private PipelineContext context(Map<Long, String> traces) {
        Pipeline pipeline =
                new Pipeline(42L, 1L, 1L, "sha", "main", "failed", "push", null, null, null, null, 1L, 1L, null);
        Job job = new Job(
                7L,
                "pytest",
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
        return new PipelineContext(pipeline, List.of(job), false, 1).withExecutionData(traces, Map.of());
    }
}
