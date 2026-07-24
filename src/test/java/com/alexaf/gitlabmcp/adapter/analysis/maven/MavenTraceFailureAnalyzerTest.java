package com.alexaf.gitlabmcp.adapter.analysis.maven;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alexaf.gitlabmcp.domain.FindingCategory;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.gitlab.diagnostics.MavenFailureAnalyzer;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;

import static org.assertj.core.api.Assertions.assertThat;

class MavenTraceFailureAnalyzerTest {

    private final MavenTraceFailureAnalyzer analyzer = new MavenTraceFailureAnalyzer(new MavenFailureAnalyzer());

    @Test
    void createsMavenSpecificFindingFromSurefireTrace() {
        String trace = """
                mvn test
                [ERROR] Failures:
                [ERROR]   CatalogTest.loads:42 expected: <true> but was: <false>
                [ERROR] Tests run: 10, Failures: 1, Errors: 0, Skipped: 0
                [INFO] BUILD FAILURE
                """;
        PipelineContext context = context(Map.of(7L, trace));

        assertThat(analyzer.supports(context)).isTrue();
        assertThat(analyzer.analyze(context)).singleElement().satisfies(finding -> {
            assertThat(finding.toolchain()).isEqualTo("maven");
            assertThat(finding.category()).isEqualTo(FindingCategory.TEST);
            assertThat(finding.summary()).containsIgnoringCase("test");
            assertThat(finding.evidence()).isNotEmpty();
        });
    }

    private PipelineContext context(Map<Long, String> traces) {
        Pipeline pipeline =
                new Pipeline(42L, 1L, 1L, "sha", "main", "failed", "push", null, null, null, null, 1L, 1L, null);
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
        return new PipelineContext(pipeline, List.of(job), false, 1).withExecutionData(traces, Map.of());
    }
}
