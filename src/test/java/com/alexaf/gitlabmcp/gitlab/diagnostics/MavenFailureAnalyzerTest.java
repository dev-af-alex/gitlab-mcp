package com.alexaf.gitlabmcp.gitlab.diagnostics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MavenFailureAnalyzerTest {

    private final MavenFailureAnalyzer analyzer = new MavenFailureAnalyzer();

    @Test
    void analyzeExtractsSurefireFailureDetails() {
        MavenFailureSummary summary = analyzer.analyze("""
                [ERROR] Failures:
                [ERROR]   OrgRegRequestClientController_CheckTest.checkOther_keyFirstAuthNotAdded:990->process:156 expected: <409> but was: <200>
                [INFO]
                [ERROR] Tests run: 2632, Failures: 1, Errors: 0, Skipped: 11
                [ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:2.22.2:test
                """);

        assertThat(summary.mavenDetected()).isTrue();
        assertThat(summary.testFailureDetected()).isTrue();
        assertThat(summary.detectedCause()).isEqualTo("Maven/Surefire test failure");
        assertThat(summary.testsRun()).isEqualTo(2632);
        assertThat(summary.failures()).isEqualTo(1);
        assertThat(summary.failingTests()).singleElement().satisfies(failure -> {
            assertThat(failure.className()).isEqualTo("OrgRegRequestClientController_CheckTest");
            assertThat(failure.methodName()).isEqualTo("checkOther_keyFirstAuthNotAdded");
            assertThat(failure.expected()).isEqualTo("409");
            assertThat(failure.actual()).isEqualTo("200");
        });
        assertThat(summary.recommendedNextTools())
                .contains("gitlab_find_job_artifact_files", "gitlab_get_merge_request_changes");
    }

    @Test
    void analyzeExtractsSurefireErrorDetails() {
        MavenFailureSummary summary = analyzer.analyze("""
                [ERROR] Errors:
                [ERROR]   OrderControllerTest » IllegalState Failed to load Applic...
                [ERROR]   ChangeStatusTest » IllegalState ApplicationContext failure threshold (1) exceeded
                [ERROR] Tests run: 2600, Failures: 0, Errors: 3, Skipped: 11
                """);

        assertThat(summary.testFailureDetected()).isTrue();
        assertThat(summary.errors()).isEqualTo(3);
        assertThat(summary.errorTests()).hasSize(2);
        assertThat(summary.errorTests().getFirst()).satisfies(error -> {
            assertThat(error.className()).isEqualTo("OrderControllerTest");
            assertThat(error.errorType()).isEqualTo("IllegalState");
            assertThat(error.contextCascade()).isFalse();
        });
        assertThat(summary.errorTests().get(1).contextCascade()).isTrue();
    }
}
