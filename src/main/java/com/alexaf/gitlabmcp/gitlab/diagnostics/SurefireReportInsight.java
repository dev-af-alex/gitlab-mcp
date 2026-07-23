package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.util.List;

public record SurefireReportInsight(
        String path,
        String className,
        Integer testsRun,
        Integer failures,
        Integer errors,
        Integer skipped,
        boolean failedToLoadApplicationContext,
        boolean contextCascade,
        String rootCauseType,
        String rootCauseMessage,
        boolean infrastructure,
        List<String> evidence,
        List<SurefireTestFailure> testFailures
) {

    private static String compactLine(String value) {
        String normalized = value.replaceAll("\\u001B\\[[;\\d]*m", "").replaceAll("\\s+", " ").strip();
        return normalized.length() > 500 ? normalized.substring(0, 500) + "..." : normalized;
    }

    public SurefireReportInsight compact() {
        List<String> compactEvidence = new java.util.ArrayList<>();
        if (testsRun != null) {
            compactEvidence.add("Tests run: " + testsRun
                    + ", Failures: " + failures
                    + ", Errors: " + errors
                    + ", Skipped: " + skipped);
        }
        if (rootCauseMessage != null) {
            compactEvidence.add(rootCauseMessage);
        }
        return new SurefireReportInsight(
                path,
                className,
                testsRun,
                failures,
                errors,
                skipped,
                failedToLoadApplicationContext,
                contextCascade,
                rootCauseType,
                rootCauseMessage,
                infrastructure,
                compactEvidence.stream().map(SurefireReportInsight::compactLine).distinct().limit(3).toList(),
                testFailures);
    }
}
