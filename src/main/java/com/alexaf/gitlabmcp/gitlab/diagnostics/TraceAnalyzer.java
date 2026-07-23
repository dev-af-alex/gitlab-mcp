package com.alexaf.gitlabmcp.gitlab.diagnostics;

import com.alexaf.gitlabmcp.gitlab.dto.Job;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

@Component
public class TraceAnalyzer {

    private final List<Rule> rules = List.of(
            new Rule("Maven test compilation failure", "high",
                    line -> containsAny(line, "compilation error", "maven-compiler-plugin", "compilation failure"),
                    List.of("Inspect the compiler error and restore the missing or incompatible test source dependency.")),
            new Rule("Maven/JUnit test failure", "high",
                    line -> containsAny(line, "tests run:", "there are test failures", "surefire-reports", "build failure"),
                    List.of("Open the test report artifact or surefire output and inspect the failing test.")),
            new Rule("Gradle test failure", "high",
                    line -> containsAny(line, "execution failed for task ':test'", "there were failing tests"),
                    List.of("Open the Gradle test report and inspect the failing test.")),
            new Rule("Out of memory", "high",
                    line -> containsAny(line, "outofmemoryerror", "java heap space", "killed", "oomkilled"),
                    List.of("Increase job memory limits or reduce memory usage in the failing step.")),
            new Rule("Java exception", "medium",
                    line -> containsAny(line, "exception in thread", "caused by:")
                            || line.matches(".*\\b[a-zA-Z0-9_.]+Exception:.*"),
                    List.of("Inspect the stack trace and check the application code around the first caused-by section.")),
            new Rule("Node.js/npm failure", "medium",
                    line -> containsAny(line, "npm err!", "yarn run", "pnpm", "elifecycle"),
                    List.of("Inspect package manager output and dependency or script changes.")),
            new Rule("Docker build failure", "medium",
                    line -> containsAny(line, "failed to solve", "docker build", "error response from daemon"),
                    List.of("Inspect the Docker build step and referenced image or Dockerfile command.")),
            new Rule("Kubernetes/Helm failure", "medium",
                    line -> containsAny(line, "error from server", "helm upgrade", "kubectl", "imagepullbackoff"),
                    List.of("Inspect Kubernetes or Helm output and cluster permissions/resources.")),
            new Rule("Timeout", "medium",
                    line -> containsAny(line, "timed out", "timeout", "deadline exceeded"),
                    List.of("Check external dependencies, runner load, and timeout configuration.")),
            new Rule("Authentication or permission failure", "high",
                    line -> containsAny(line, "401 unauthorized", "403 forbidden", "permission denied", "access denied"),
                    List.of("Verify credentials, token scopes, and project or registry permissions."))
    );

    private static boolean containsAny(String value, String... needles) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (normalized.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public TraceAnalysis analyze(Job job, String trace) {
        List<String> evidence = baseEvidence(job);
        if (!StringUtils.hasText(trace)) {
            return new TraceAnalysis(fallbackCause(job), "unknown", evidence, fallbackSteps(job, evidence));
        }

        String[] lines = trace.split("\\R");
        for (Rule rule : rules) {
            if (matchesAnyLine(lines, rule)) {
                evidence.addAll(evidenceLines(lines, rule));
                return new TraceAnalysis(rule.detectedCause(), rule.confidence(), List.copyOf(evidence), rule.nextSteps());
            }
        }
        evidence.addAll(lastInterestingLines(lines));
        return new TraceAnalysis(fallbackCause(job), "low", List.copyOf(evidence), fallbackSteps(job, evidence));
    }

    private List<String> baseEvidence(Job job) {
        List<String> evidence = new ArrayList<>();
        if (StringUtils.hasText(job.failureReason())) {
            evidence.add("failure_reason: " + job.failureReason());
        }
        return evidence;
    }

    private boolean matchesAnyLine(String[] lines, Rule rule) {
        for (String line : lines) {
            if (rule.matches().test(line.strip())) {
                return true;
            }
        }
        return false;
    }

    private List<String> evidenceLines(String[] lines, Rule winningRule) {
        List<String> matched = new ArrayList<>();
        for (String line : lines) {
            String stripped = line.strip();
            if (StringUtils.hasText(stripped) && winningRule.matches().test(stripped)) {
                matched.add(stripped);
            }
        }
        if (!matched.isEmpty()) {
            return matched.stream().limit(8).toList();
        }
        return lastInterestingLines(lines);
    }

    private List<String> lastInterestingLines(String[] lines) {
        List<String> result = new ArrayList<>();
        for (int i = lines.length - 1; i >= 0 && result.size() < 5; i--) {
            String line = lines[i].strip();
            if (StringUtils.hasText(line) && !line.startsWith("[truncated to ")) {
                result.add(0, line);
            }
        }
        return result;
    }

    private String fallbackCause(Job job) {
        return StringUtils.hasText(job.failureReason()) ? job.failureReason() : "Failed job";
    }

    private List<String> fallbackSteps(Job job, List<String> evidence) {
        List<String> steps = new ArrayList<>();
        if (!evidence.isEmpty()) {
            steps.add("Inspect the evidence lines and the job trace around the first failing command.");
        }
        if (StringUtils.hasText(job.webUrl())) {
            steps.add("Open the failed job in GitLab: " + job.webUrl());
        }
        if (steps.isEmpty()) {
            steps.add("Open the failed job trace in GitLab and inspect the last failing command.");
        }
        return List.copyOf(steps);
    }

    private record Rule(
            String detectedCause,
            String confidence,
            Predicate<String> matches,
            List<String> nextSteps
    ) {
    }
}
