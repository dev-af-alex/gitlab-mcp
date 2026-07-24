package com.alexaf.gitlabmcp.application.pipeline;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alexaf.gitlabmcp.domain.Confidence;
import com.alexaf.gitlabmcp.domain.Finding;
import com.alexaf.gitlabmcp.domain.FindingCategory;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.alexaf.gitlabmcp.port.FailureAnalyzer;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineAnalysisEngineTest {

    @Test
    void executesSupportedAnalyzersInPriorityOrder() {
        FailureAnalyzer fallback = analyzer("fallback", 0);
        FailureAnalyzer structured = analyzer("structured", 100);
        PipelineAnalysisEngine engine = new PipelineAnalysisEngine(List.of(fallback, structured));

        var result = engine.analyze(context());

        assertThat(result.analyzers()).containsExactly("structured", "fallback");
        assertThat(result.findings()).extracting(Finding::summary).containsExactly("structured", "fallback");
    }

    private FailureAnalyzer analyzer(String id, int priority) {
        return new FailureAnalyzer() {
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
                return true;
            }

            @Override
            public List<Finding> analyze(PipelineContext context) {
                return List.of(new Finding(FindingCategory.UNKNOWN, id, Confidence.LOW, id, List.of(), List.of()));
            }
        };
    }

    private PipelineContext context() {
        Pipeline pipeline =
                new Pipeline(42L, 1L, 1L, "sha", "main", "failed", "push", null, null, null, null, 1L, 1L, null);
        return new PipelineContext(pipeline, List.of(), false, 0);
    }
}
