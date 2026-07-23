package com.alexaf.gitlabmcp.adapter.analysis.junit;

import com.alexaf.gitlabmcp.domain.Confidence;
import com.alexaf.gitlabmcp.domain.Evidence;
import com.alexaf.gitlabmcp.domain.Finding;
import com.alexaf.gitlabmcp.domain.FindingCategory;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.gitlab.dto.GitlabTestCase;
import com.alexaf.gitlabmcp.gitlab.dto.GitlabTestReport;
import com.alexaf.gitlabmcp.gitlab.dto.GitlabTestSuite;
import com.alexaf.gitlabmcp.port.FailureAnalyzer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class GitlabTestReportAnalyzer implements FailureAnalyzer {

    private static final int MAX_FINDINGS = 100;

    @Override
    public String id() {
        return "gitlab-test-report";
    }

    @Override
    public int priority() {
        return 400;
    }

    @Override
    public boolean supports(PipelineContext context) {
        return context.testReport() != null;
    }

    @Override
    public List<Finding> analyze(PipelineContext context) {
        GitlabTestReport report = context.testReport();
        List<Finding> findings = new ArrayList<>();
        for (GitlabTestSuite suite : report.testSuites()) {
            for (GitlabTestCase testCase : suite.testCases()) {
                if (!isFailure(testCase) || findings.size() >= MAX_FINDINGS) {
                    continue;
                }
                findings.add(finding(suite, testCase));
            }
        }
        if (findings.isEmpty() && positive(report.failedCount()) + positive(report.errorCount()) > 0) {
            findings.add(new Finding(
                    FindingCategory.TEST,
                    "junit",
                    Confidence.HIGH,
                    "GitLab reports " + positive(report.failedCount()) + " failed and "
                            + positive(report.errorCount()) + " errored tests",
                    List.of(new Evidence(
                            "gitlab-test-report",
                            null,
                            null,
                            "No individual failed test cases were returned by GitLab")),
                    List.of("Inspect the job artifacts or trace for the missing test case details.")));
        }
        return List.copyOf(findings);
    }

    private Finding finding(GitlabTestSuite suite, GitlabTestCase testCase) {
        String qualifiedName = StringUtils.hasText(testCase.classname())
                ? testCase.classname() + "#" + testCase.name()
                : testCase.name();
        List<Evidence> evidence = new ArrayList<>();
        if (StringUtils.hasText(testCase.systemOutput())) {
            evidence.add(new Evidence(
                    "gitlab-test-report",
                    null,
                    suite.name(),
                    compact(testCase.systemOutput())));
        }
        if (StringUtils.hasText(testCase.stackTrace())) {
            evidence.add(new Evidence(
                    "gitlab-test-report",
                    null,
                    suite.name(),
                    compact(testCase.stackTrace())));
        }
        if (evidence.isEmpty()) {
            evidence.add(new Evidence(
                    "gitlab-test-report",
                    null,
                    suite.name(),
                    "status=" + testCase.status()));
        }
        return new Finding(
                FindingCategory.TEST,
                "junit",
                Confidence.HIGH,
                "Test " + qualifiedName + " " + testCase.status(),
                evidence,
                List.of("Inspect the failing test and the code exercised by it."));
    }

    private boolean isFailure(GitlabTestCase testCase) {
        return "failed".equalsIgnoreCase(testCase.status())
                || "error".equalsIgnoreCase(testCase.status());
    }

    private int positive(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private String compact(String value) {
        String compact = value.replaceAll("\\s+", " ").strip();
        return compact.length() <= 2_000 ? compact : compact.substring(0, 2_000) + " [truncated]";
    }
}
