package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.util.List;

public record RootCauseSummary(
        String type,
        String testClass,
        String message,
        boolean infrastructure,
        String recommendation,
        String confidence,
        List<String> evidence) {

    private static String compactLine(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\u001B\\[[;\\d]*m", "")
                .replaceAll("\\s+", " ")
                .strip();
        return normalized.length() > 500 ? normalized.substring(0, 500) + "..." : normalized;
    }

    public RootCauseSummary compact() {
        return new RootCauseSummary(
                type,
                testClass,
                compactLine(message),
                infrastructure,
                recommendation,
                confidence,
                evidence.stream()
                        .map(RootCauseSummary::compactLine)
                        .distinct()
                        .limit(4)
                        .toList());
    }
}
