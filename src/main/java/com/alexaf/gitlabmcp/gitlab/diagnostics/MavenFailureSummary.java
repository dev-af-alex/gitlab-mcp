package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.util.List;

public record MavenFailureSummary(
        boolean mavenDetected,
        boolean testFailureDetected,
        String detectedCause,
        String confidence,
        Integer testsRun,
        Integer failures,
        Integer errors,
        Integer skipped,
        List<MavenTestFailure> failingTests,
        List<MavenTestError> errorTests,
        List<String> evidence,
        List<String> surefireHints,
        List<String> recommendedNextTools) {

    private static String compactLine(String value) {
        String normalized = value.replaceAll("\\u001B\\[[;\\d]*m", "")
                .replaceAll("\\s+", " ")
                .strip();
        return normalized.length() > 500 ? normalized.substring(0, 500) + "..." : normalized;
    }

    public boolean executionFailureDetected() {
        return evidence.stream()
                .anyMatch(line -> line.contains("forked VM terminated")
                        || line.contains("Process Exit Code:")
                        || line.contains("SurefireBooterForkException"));
    }

    public boolean compilationFailureDetected() {
        return evidence.stream()
                .anyMatch(line -> line.contains("COMPILATION ERROR")
                        || line.contains("maven-compiler-plugin")
                        || line.contains("Compilation failure"));
    }

    public MavenFailureSummary compact() {
        return new MavenFailureSummary(
                mavenDetected,
                testFailureDetected,
                detectedCause,
                confidence,
                testsRun,
                failures,
                errors,
                skipped,
                failingTests,
                errorTests.stream().filter(error -> !error.contextCascade()).toList(),
                evidence.stream()
                        .map(MavenFailureSummary::compactLine)
                        .distinct()
                        .limit(8)
                        .toList(),
                surefireHints.stream()
                        .map(MavenFailureSummary::compactLine)
                        .distinct()
                        .limit(4)
                        .toList(),
                recommendedNextTools);
    }
}
