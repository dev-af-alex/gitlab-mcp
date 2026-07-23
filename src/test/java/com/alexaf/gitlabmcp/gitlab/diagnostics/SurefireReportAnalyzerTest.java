package com.alexaf.gitlabmcp.gitlab.diagnostics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SurefireReportAnalyzerTest {

    private final SurefireReportAnalyzer analyzer = new SurefireReportAnalyzer();

    @Test
    void analyzeClassifiesTestcontainersStartupFailureAsInfrastructure() {
        SurefireReportInsight insight = analyzer.analyze("target/surefire-reports/OrderControllerTest.txt", """
                Test set: com.example.OrderControllerTest
                Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
                java.lang.IllegalStateException: Failed to load ApplicationContext
                Caused by: org.testcontainers.containers.ContainerLaunchException: Container startup failed for image ryuk:0.6.0
                Status 500: {"message":"E: oscap: dpkginfo_init has failed. sh: bash: not found"}
                """);

        assertThat(insight.className()).isEqualTo("com.example.OrderControllerTest");
        assertThat(insight.errors()).isEqualTo(1);
        assertThat(insight.failedToLoadApplicationContext()).isTrue();
        assertThat(insight.rootCauseType()).isEqualTo("testcontainers_container_startup");
        assertThat(insight.infrastructure()).isTrue();
        assertThat(insight.evidence()).anySatisfy(line -> assertThat(line).contains("Container startup failed"));
    }

    @Test
    void analyzeMarksApplicationContextThresholdAsCascade() {
        SurefireReportInsight insight = analyzer.analyze("target/surefire-reports/ChangeStatusTest.txt", """
                Test set: com.example.ChangeStatusTest
                Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
                java.lang.IllegalStateException: ApplicationContext failure threshold (1) exceeded
                """);

        assertThat(insight.contextCascade()).isTrue();
        assertThat(insight.rootCauseType()).isEqualTo("application_context_cascade");
        assertThat(insight.infrastructure()).isFalse();
    }
}
