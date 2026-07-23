package com.alexaf.gitlabmcp.gitlab.diagnostics;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MavenFailureAnalyzer {

    private static final Pattern TEST_COUNTS = Pattern.compile(
            "Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+)(?:,\\s*Errors:\\s*(\\d+))?(?:,\\s*Skipped:\\s*(\\d+))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FAILURE_LINE = Pattern.compile(
            "\\[ERROR]\\s+([^\\s:]+)\\.([^\\s:]+):\\d+(?:->\\S+)?(?:\\s+(.*))?");
    private static final Pattern FAILURE_LINE_WITHOUT_LINE_NUMBER = Pattern.compile(
            "\\[ERROR]\\s+([^\\s:]+)\\.([^\\s:]+)(?:\\s+(.*))?");
    private static final Pattern ERROR_LINE = Pattern.compile(
            "\\[ERROR]\\s+([^\\s»]+)(?:\\.([^\\s»]+))?\\s+»\\s+([^\\s]+)\\s*(.*)");
    private static final Pattern EXPECTED_ACTUAL = Pattern.compile(
            "expected:\\s*<([^>]*)>\\s*but was:\\s*<([^>]*)>", Pattern.CASE_INSENSITIVE);

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsExecutionFailure(String trace) {
        String lower = trace.toLowerCase();
        return lower.contains("forked vm terminated")
                || lower.contains("surefirebooterforkexception")
                || lower.contains("process exit code: 137");
    }

    private static boolean isApplicationContextCascade(String message) {
        return message.toLowerCase().contains("applicationcontext failure threshold");
    }

    public MavenFailureSummary analyze(String trace) {
        if (!StringUtils.hasText(trace)) {
            return empty();
        }

        boolean mavenDetected = containsAny(trace, "[INFO] BUILD", "[ERROR]", "maven-surefire-plugin", "surefire-reports");
        Counts counts = counts(trace);
        List<MavenTestFailure> failingTests = failingTests(trace);
        List<MavenTestError> errorTests = errorTests(trace);
        List<String> evidence = evidence(trace, counts, failingTests, errorTests);
        List<String> surefireHints = surefireHints(trace);
        boolean testFailureDetected = counts.failures() != null && counts.failures() > 0
                || counts.errors() != null && counts.errors() > 0
                || !failingTests.isEmpty()
                || !errorTests.isEmpty()
                || trace.toLowerCase().contains("there are test failures")
                || containsExecutionFailure(trace);

        String cause = containsExecutionFailure(trace)
                       ? "Maven/Surefire test execution failure"
                       : testFailureDetected ? "Maven/Surefire test failure"
                                             : mavenDetected ? "Maven build failure" : "No Maven failure detected";

        return new MavenFailureSummary(
                mavenDetected,
                testFailureDetected,
                cause,
                testFailureDetected ? "high" : mavenDetected ? "medium" : "low",
                counts.testsRun(),
                counts.failures(),
                counts.errors(),
                counts.skipped(),
                failingTests,
                errorTests,
                evidence,
                surefireHints,
                recommendedNextTools(testFailureDetected, surefireHints));
    }

    private MavenFailureSummary empty() {
        return new MavenFailureSummary(false, false, "No trace", "unknown",
                null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private Counts counts(String trace) {
        Matcher matcher = TEST_COUNTS.matcher(trace);
        Counts last = new Counts(null, null, null, null);
        while (matcher.find()) {
            last = new Counts(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3)),
                    matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4)));
        }
        return last;
    }

    private List<MavenTestFailure> failingTests(String trace) {
        List<MavenTestFailure> result = new ArrayList<>();
        for (String line : trace.split("\\R")) {
            MavenTestFailure failure = testFailure(line.strip());
            if (failure != null) {
                result.add(failure);
            }
        }
        return result.stream().limit(20).toList();
    }

    private MavenTestFailure testFailure(String line) {
        Matcher matcher = FAILURE_LINE.matcher(line);
        if (!matcher.matches()) {
            matcher = FAILURE_LINE_WITHOUT_LINE_NUMBER.matcher(line);
        }
        if (!matcher.matches()) {
            return null;
        }
        String message = matcher.group(3) == null ? "" : matcher.group(3).strip();
        Matcher expectedActual = EXPECTED_ACTUAL.matcher(message);
        String expected = null;
        String actual = null;
        if (expectedActual.find()) {
            expected = expectedActual.group(1);
            actual = expectedActual.group(2);
        }
        return new MavenTestFailure(matcher.group(1), matcher.group(2), message, expected, actual);
    }

    private List<MavenTestError> errorTests(String trace) {
        List<MavenTestError> result = new ArrayList<>();
        for (String line : trace.split("\\R")) {
            Matcher matcher = ERROR_LINE.matcher(line.strip());
            if (matcher.matches()) {
                String message = matcher.group(4) == null ? "" : matcher.group(4).strip();
                result.add(new MavenTestError(
                        matcher.group(1),
                        matcher.group(2),
                        matcher.group(3),
                        message,
                        isApplicationContextCascade(message)));
            }
        }
        return result.stream().limit(20).toList();
    }

    private List<String> evidence(
            String trace,
            Counts counts,
            List<MavenTestFailure> failingTests,
            List<MavenTestError> errorTests) {
        List<String> evidence = new ArrayList<>();
        if (counts.testsRun() != null) {
            evidence.add("Tests run: " + counts.testsRun()
                    + ", Failures: " + counts.failures()
                    + ", Errors: " + counts.errors()
                    + ", Skipped: " + counts.skipped());
        }
        failingTests.stream()
                .map(failure -> failure.className() + "." + failure.methodName() + ": " + failure.message())
                .forEach(evidence::add);
        errorTests.stream()
                .map(error -> error.className()
                        + (StringUtils.hasText(error.methodName()) ? "." + error.methodName() : "")
                        + " » " + error.errorType() + " " + error.message())
                .forEach(evidence::add);
        int compilationLocations = 0;
        for (String line : trace.split("\\R")) {
            String stripped = line.strip();
            if (stripped.contains("[ERROR] Failures:")
                    || stripped.contains("[ERROR] Errors:")
                    || stripped.contains("There are test failures")
                    || stripped.contains("BUILD FAILURE")) {
                evidence.add(stripped);
            }
            if (stripped.contains("COMPILATION ERROR")
                    || stripped.contains("Compilation failure")
                    || stripped.contains("maven-compiler-plugin")
                    || stripped.startsWith("[ERROR]   symbol:")
                    || stripped.startsWith("[ERROR]   location:")
                    || (compilationLocations < 5
                    && stripped.matches("\\[ERROR] /.*\\.java:\\[?\\d+(,\\d+)?].*"))) {
                evidence.add(stripped);
            }
            if (stripped.matches("\\[ERROR] /.*\\.java:\\[?\\d+(,\\d+)?].*")) {
                compilationLocations++;
            }
            if (stripped.contains("forked VM terminated")
                    || stripped.contains("Process Exit Code:")
                    || stripped.contains("SurefireBooterForkException")
                    || stripped.startsWith("[ERROR] Crashed tests:")) {
                evidence.add(stripped);
            }
        }
        return evidence.stream().distinct().limit(20).toList();
    }

    private List<String> surefireHints(String trace) {
        Set<String> hints = new LinkedHashSet<>();
        for (String line : trace.split("\\R")) {
            String stripped = line.strip();
            if (stripped.contains("surefire-reports")) {
                hints.add(stripped);
            }
        }
        return hints.stream().limit(10).toList();
    }

    private List<String> recommendedNextTools(boolean testFailureDetected, List<String> surefireHints) {
        if (!testFailureDetected) {
            return List.of("gitlab_get_job_trace_matches");
        }
        List<String> tools = new ArrayList<>();
        tools.add("gitlab_find_job_artifact_files");
        if (!surefireHints.isEmpty()) {
            tools.add("gitlab_get_job_artifact_file");
        }
        tools.add("gitlab_get_merge_request_changes");
        return List.copyOf(tools);
    }

    public List<String> failedClassNames(MavenFailureSummary summary) {
        List<String> result = new ArrayList<>();
        summary.failingTests().stream().map(MavenTestFailure::className).forEach(result::add);
        summary.errorTests().stream().map(MavenTestError::className).forEach(result::add);
        return result.stream().filter(StringUtils::hasText).distinct().toList();
    }

    public MavenFailureSummary merge(MavenFailureSummary first, MavenFailureSummary second) {
        if (second == null || !second.mavenDetected()) {
            return first;
        }
        if (first == null || !first.mavenDetected()) {
            return second;
        }
        List<MavenTestFailure> failures = distinctFailures(first.failingTests(), second.failingTests());
        List<MavenTestError> errors = distinctErrors(first.errorTests(), second.errorTests());
        boolean testFailure = first.testFailureDetected() || second.testFailureDetected();
        boolean executionFailure = first.executionFailureDetected() || second.executionFailureDetected();
        String cause = executionFailure
                       ? "Maven/Surefire test execution failure"
                       : testFailure ? "Maven/Surefire test failure"
                                     : "Maven build failure";
        return new MavenFailureSummary(
                true,
                testFailure,
                cause,
                testFailure ? "high" : "medium",
                prefer(first.testsRun(), second.testsRun()),
                prefer(first.failures(), second.failures()),
                prefer(first.errors(), second.errors()),
                prefer(first.skipped(), second.skipped()),
                failures,
                errors,
                distinct(first.evidence(), second.evidence(), 20),
                distinct(first.surefireHints(), second.surefireHints(), 10),
                recommendedNextTools(testFailure, distinct(first.surefireHints(), second.surefireHints(), 10)));
    }

    private List<MavenTestFailure> distinctFailures(List<MavenTestFailure> first, List<MavenTestFailure> second) {
        Set<String> seen = new LinkedHashSet<>();
        return java.util.stream.Stream.concat(first.stream(), second.stream())
                .filter(failure -> seen.add(failure.className() + "#" + failure.methodName()))
                .limit(20)
                .toList();
    }

    private List<MavenTestError> distinctErrors(List<MavenTestError> first, List<MavenTestError> second) {
        Set<String> seen = new LinkedHashSet<>();
        return java.util.stream.Stream.concat(first.stream(), second.stream())
                .filter(error -> seen.add(error.className() + "#" + error.methodName()))
                .limit(20)
                .toList();
    }

    private List<String> distinct(List<String> first, List<String> second, int limit) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).distinct().limit(limit).toList();
    }

    private Integer prefer(Integer first, Integer second) {
        return first != null ? first : second;
    }

    private record Counts(Integer testsRun, Integer failures, Integer errors, Integer skipped) {
    }
}
