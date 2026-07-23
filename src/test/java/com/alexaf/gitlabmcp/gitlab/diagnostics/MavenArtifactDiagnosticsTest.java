package com.alexaf.gitlabmcp.gitlab.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class MavenArtifactDiagnosticsTest {

    private static final String REPORT_DIRECTORY = "target/surefire-reports/";
    private static final String TEST_CLASS = "com.example.orders.ExampleControllerTest";

    private final SurefireReportAnalyzer reportAnalyzer = new SurefireReportAnalyzer();
    private final MavenFailureAnalyzer mavenAnalyzer = new MavenFailureAnalyzer();

    @TempDir
    Path tempDirectory;

    private static void add(ZipOutputStream output, String name, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static boolean hasFailure(SurefireReportInsight insight) {
        return (insight.failures() != null && insight.failures() > 0)
                || (insight.errors() != null && insight.errors() > 0);
    }

    private static String read(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesFailedSurefireReportsWithoutReturningPassingTestNoise() throws IOException {
        try (ZipFile zip = syntheticArchive()) {
            int failedReports = 0;
            int failedCases = 0;
            for (ZipEntry entry : zip.stream().filter(candidate -> candidate.getName().endsWith(".txt")).toList()) {
                SurefireReportInsight insight = reportAnalyzer.analyze(entry.getName(), read(zip, entry));
                if (hasFailure(insight)) {
                    failedReports++;
                    failedCases += insight.testFailures().size();
                }
            }

            assertThat(failedReports).isEqualTo(1);
            assertThat(failedCases).isEqualTo(2);
        }
    }

    @Test
    void parsesIndividualFailureDetailsFromTxtReport() throws IOException {
        try (ZipFile zip = syntheticArchive()) {
            ZipEntry entry = zip.getEntry(REPORT_DIRECTORY + "ExampleControllerTest.txt");

            SurefireReportInsight insight = reportAnalyzer.analyze(entry.getName(), read(zip, entry));

            assertThat(insight.className()).isEqualTo(TEST_CLASS);
            assertThat(insight.failures()).isEqualTo(2);
            assertThat(insight.testFailures()).extracting(SurefireTestFailure::methodName)
                    .containsExactly("returnsFirstExample", "returnsSecondExample");
            assertThat(insight.testFailures()).allSatisfy(test -> {
                assertThat(test.exceptionType()).isEqualTo("org.opentest4j.AssertionFailedError");
                assertThat(test.message()).contains("expected");
                assertThat(test.sourceLocation()).endsWith("ExampleControllerTest.java:42");
            });
        }
    }

    @Test
    void parsesXmlAsFallbackAndKeepsOnlyFailedTestCases() throws IOException {
        try (ZipFile zip = syntheticArchive()) {
            ZipEntry entry = zip.getEntry(REPORT_DIRECTORY + "TEST-" + TEST_CLASS + ".xml");

            SurefireReportInsight insight = reportAnalyzer.analyze(entry.getName(), read(zip, entry));

            assertThat(insight.className()).isEqualTo(TEST_CLASS);
            assertThat(insight.testsRun()).isEqualTo(3);
            assertThat(insight.failures()).isEqualTo(1);
            assertThat(insight.testFailures()).singleElement()
                    .satisfies(failure -> assertThat(failure.methodName()).isEqualTo("returnsFirstExample"));
        }
    }

    @Test
    void identifiesCompilationFailureFromArchivedMavenLog() throws IOException {
        try (ZipFile zip = syntheticArchive()) {
            ZipEntry log = zip.getEntry("build.log");
            MavenFailureSummary summary = mavenAnalyzer.analyze(read(zip, log));

            assertThat(summary.mavenDetected()).isTrue();
            assertThat(summary.testFailureDetected()).isFalse();
            assertThat(summary.compilationFailureDetected()).isTrue();
            assertThat(summary.evidence()).anyMatch(line -> line.contains("ExampleRepository"));
        }
    }

    @Test
    void classifiesForkExitAsMavenExecutionFailure() {
        MavenFailureSummary summary = mavenAnalyzer.analyze("""
                [INFO] BUILD FAILURE
                [ERROR] ExecutionException The forked VM terminated without properly saying goodbye.
                [ERROR] Process Exit Code: 137
                [ERROR] Crashed tests:
                [ERROR] com.example.DownloadControllerTest
                """);

        assertThat(summary.testFailureDetected()).isTrue();
        assertThat(summary.detectedCause()).isEqualTo("Maven/Surefire test execution failure");
        assertThat(summary.executionFailureDetected()).isTrue();
    }

    private ZipFile syntheticArchive() throws IOException {
        Path archive = tempDirectory.resolve("synthetic-maven-artifacts.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(output, REPORT_DIRECTORY + "ExampleControllerTest.txt", """
                    Test set: com.example.orders.ExampleControllerTest
                    Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
                    returnsFirstExample  Time elapsed: 0.1 s  <<< FAILURE!
                    org.opentest4j.AssertionFailedError: expected: <first> but was: <other>
                        at com.example.orders.ExampleControllerTest.returnsFirstExample(ExampleControllerTest.java:42)
                    returnsSecondExample  Time elapsed: 0.1 s  <<< FAILURE!
                    org.opentest4j.AssertionFailedError: expected: <second> but was: <other>
                        at com.example.orders.ExampleControllerTest.returnsSecondExample(ExampleControllerTest.java:42)
                    """);
            add(output, REPORT_DIRECTORY + "PassingTest.txt", """
                    Test set: com.example.orders.PassingTest
                    Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
                    """);
            add(output, REPORT_DIRECTORY + "TEST-" + TEST_CLASS + ".xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <testsuite name="com.example.orders.ExampleControllerTest"
                               tests="3" failures="1" errors="0" skipped="0">
                      <testcase name="returnsFirstExample" classname="com.example.orders.ExampleControllerTest">
                        <failure type="org.opentest4j.AssertionFailedError"
                                 message="expected: &lt;first&gt; but was: &lt;other&gt;">
                          at com.example.orders.ExampleControllerTest.returnsFirstExample(ExampleControllerTest.java:42)
                        </failure>
                      </testcase>
                      <testcase name="returnsSecondExample" classname="com.example.orders.ExampleControllerTest"/>
                      <testcase name="returnsThirdExample" classname="com.example.orders.ExampleControllerTest"/>
                    </testsuite>
                    """);
            add(output, "build.log", """
                    [INFO] BUILD FAILURE
                    [ERROR] COMPILATION ERROR :
                    [ERROR] /workspace/src/main/java/com/example/ExampleRepository.java:[12,34] cannot find symbol
                    [ERROR]   symbol: class MissingType
                    [ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:compile
                    """);
        }
        return new ZipFile(archive.toFile());
    }
}
