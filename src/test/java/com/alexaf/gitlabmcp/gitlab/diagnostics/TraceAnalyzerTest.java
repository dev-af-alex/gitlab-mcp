package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alexaf.gitlabmcp.gitlab.dto.Job;

import static org.assertj.core.api.Assertions.assertThat;

class TraceAnalyzerTest {

    private final TraceAnalyzer analyzer = new TraceAnalyzer();

    private static Job job(String failureReason) {
        return new Job(
                8L,
                "test",
                "test",
                "failed",
                failureReason,
                "https://gitlab.example/jobs/8",
                "main",
                false,
                false,
                null,
                null,
                null,
                1.0,
                0.1,
                List.of());
    }

    @Test
    void analyzeDetectsMavenJUnitFailure() {
        TraceAnalysis analysis = analyzer.analyze(job("script_failure"), """
                [INFO] Running ServiceTest
                Tests run: 12, Failures: 1, Errors: 0
                Please refer to target/surefire-reports
                """);

        assertThat(analysis.detectedCause()).isEqualTo("Maven/JUnit test failure");
        assertThat(analysis.confidence()).isEqualTo("high");
        assertThat(analysis.evidence()).contains("failure_reason: script_failure");
        assertThat(analysis.evidence()).anyMatch(line -> line.contains("Tests run: 12"));
    }

    @Test
    void analyzePrioritizesMavenFailureOverLaterTimeoutNoise() {
        TraceAnalysis analysis = analyzer.analyze(job("script_failure"), """
                [ERROR] Failures:
                [ERROR]   ServiceTest.test:42 expected: <409> but was: <200>
                [ERROR] Tests run: 10, Failures: 1, Errors: 0, Skipped: 0
                [ERROR] BUILD FAILURE
                org.apache.kafka.clients.NetworkClient request timeout: 30000ms
                org.apache.kafka.clients.NetworkClient request timeout: 30000ms
                org.apache.kafka.clients.NetworkClient request timeout: 30000ms
                """);

        assertThat(analysis.detectedCause()).isEqualTo("Maven/JUnit test failure");
        assertThat(analysis.evidence()).anyMatch(line -> line.contains("BUILD FAILURE"));
    }

    @Test
    void analyzeDetectsOutOfMemoryBeforeGenericJavaException() {
        TraceAnalysis analysis = analyzer.analyze(job(null), """
                Exception in thread "main" java.lang.OutOfMemoryError: Java heap space
                at example.Service.run(Service.java:42)
                """);

        assertThat(analysis.detectedCause()).isEqualTo("Out of memory");
        assertThat(analysis.confidence()).isEqualTo("high");
    }

    @Test
    void analyzeDetectsAuthenticationFailure() {
        TraceAnalysis analysis = analyzer.analyze(job(null), "registry.example responded 401 Unauthorized");

        assertThat(analysis.detectedCause()).isEqualTo("Authentication or permission failure");
        assertThat(analysis.nextSteps())
                .containsExactly("Verify credentials, token scopes, and project or registry permissions.");
    }

    @Test
    void analyzeFallsBackToLastInterestingLines() {
        TraceAnalysis analysis = analyzer.analyze(job("script_failure"), """
                line 1
                line 2
                line 3
                line 4
                line 5
                line 6
                """);

        assertThat(analysis.detectedCause()).isEqualTo("script_failure");
        assertThat(analysis.confidence()).isEqualTo("low");
        assertThat(analysis.evidence()).contains("line 2", "line 6");
        assertThat(analysis.evidence()).doesNotContain("line 1");
    }
}
