package com.alexaf.gitlabmcp.adapter.analysis.junit;

import com.alexaf.gitlabmcp.domain.Confidence;
import com.alexaf.gitlabmcp.domain.FindingCategory;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.gitlab.dto.GitlabTestCase;
import com.alexaf.gitlabmcp.gitlab.dto.GitlabTestReport;
import com.alexaf.gitlabmcp.gitlab.dto.GitlabTestSuite;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitlabTestReportAnalyzerTest {

    private final GitlabTestReportAnalyzer analyzer = new GitlabTestReportAnalyzer();

    @Test
    void analyzesPytestFailureFromLanguageIndependentGitlabReport() {
        GitlabTestCase testCase = new GitlabTestCase(
                "failed",
                "test_checkout",
                "tests.test_checkout",
                0.4,
                "expected status 200",
                "AssertionError: assert 500 == 200");
        GitlabTestSuite suite = new GitlabTestSuite(
                "pytest",
                1.0,
                2,
                1,
                1,
                0,
                0,
                List.of(testCase));
        GitlabTestReport report = new GitlabTestReport(
                1.0,
                2,
                1,
                1,
                0,
                0,
                List.of(suite));
        PipelineContext context = new PipelineContext(
                pipeline(),
                List.of(),
                report,
                false,
                0);

        var findings = analyzer.analyze(context);

        assertThat(findings).singleElement()
                .satisfies(finding -> {
                    assertThat(finding.category()).isEqualTo(FindingCategory.TEST);
                    assertThat(finding.toolchain()).isEqualTo("junit");
                    assertThat(finding.confidence()).isEqualTo(Confidence.HIGH);
                    assertThat(finding.summary())
                            .contains("tests.test_checkout#test_checkout", "failed");
                    assertThat(finding.evidence())
                            .extracting(evidence -> evidence.message())
                            .anyMatch(message -> message.contains("AssertionError"));
                });
    }

    private Pipeline pipeline() {
        return new Pipeline(42L, 1L, 1L, "sha", "main", "failed", "push",
                null, null, null, null, 1L, 1L, null);
    }
}
