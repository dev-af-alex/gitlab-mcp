package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SurefireReportAnalyzer { // NOPMD - existing complexity baseline

    private static final Pattern TEST_SET = Pattern.compile("Test set:\\s*(\\S+)");
    private static final Pattern TXT_TEST_CASE =
            Pattern.compile("^\\s*(\\S+)\\s+Time elapsed:.*<<<\\s*(FAILURE|ERROR)!.*$");
    private static final Pattern EXCEPTION = Pattern.compile(
            "^\\s*([a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)*(?:Exception|Error|Failure))(?::\\s*(.*))?$");
    private static final Pattern STACK_FRAME = Pattern.compile("^\\s*at\\s+[^\\s(]+\\(([^)]+\\.java:\\d+)\\).*$");
    private static final int MAX_TEST_FAILURES = 20;
    private static final int MAX_MESSAGE_LENGTH = 600;
    private static final Pattern TEST_COUNTS = Pattern.compile(
            "Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private static boolean containsIgnoreCase(String value, String needle) {
        return value.toLowerCase().contains(needle.toLowerCase());
    }

    public SurefireReportInsight analyze(String path, String text) {
        String value = text == null ? "" : text;
        Counts counts = counts(value);
        List<SurefireTestFailure> testFailures = isXml(value) ? xmlTestFailures(value) : textTestFailures(value);
        List<String> evidence = evidence(value);
        String rootCauseMessage = rootCauseMessage(value, evidence, testFailures);
        String rootCauseType = rootCauseType(value, testFailures);
        boolean infrastructure = isInfrastructure(value);
        return new SurefireReportInsight(
                path,
                className(path, value),
                counts.testsRun(),
                counts.failures(),
                counts.errors(),
                counts.skipped(),
                containsIgnoreCase(value, "Failed to load ApplicationContext"),
                containsIgnoreCase(value, "ApplicationContext failure threshold"),
                rootCauseType,
                rootCauseMessage,
                infrastructure,
                evidence,
                testFailures);
    }

    private Counts counts(String text) {
        Matcher matcher = TEST_COUNTS.matcher(text);
        Counts last = new Counts(null, null, null, null);
        while (matcher.find()) {
            last = new Counts(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4)));
        }
        if (last.testsRun() == null && isXml(text)) {
            try {
                Element suite = parseXml(text).getDocumentElement();
                last = new Counts(
                        attributeInt(suite, "tests"),
                        attributeInt(suite, "failures"),
                        attributeInt(suite, "errors"),
                        attributeInt(suite, "skipped"));
            } catch (Exception ignored) {
                // A malformed or truncated XML report is still useful for text-level evidence.
            }
        }
        return last;
    }

    private String className(String path, String text) {
        Matcher matcher = TEST_SET.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (isXml(text)) {
            try {
                return textOrNull(parseXml(text).getDocumentElement().getAttribute("name"));
            } catch (Exception ignored) {
                // Fall back to the artifact file name below.
            }
        }
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.startsWith("TEST-")) {
            name = name.substring("TEST-".length());
        }
        if (name.endsWith(".txt") || name.endsWith(".xml")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    private List<String> evidence(String text) {
        List<String> result = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String stripped = line.strip();
            if (!StringUtils.hasText(stripped)) {
                continue;
            }
            if (interesting(stripped)) {
                result.add(stripped);
            }
        }
        return result.stream().distinct().limit(20).toList();
    }

    private String rootCauseMessage(String text, List<String> evidence, List<SurefireTestFailure> testFailures) {
        return evidence.stream()
                .filter(line -> containsIgnoreCase(line, "Container startup failed")
                        || containsIgnoreCase(line, "Status 500")
                        || containsIgnoreCase(line, "bash: not found")
                        || containsIgnoreCase(line, "oscap"))
                .findFirst()
                .or(() -> evidence.stream()
                        .filter(line -> containsIgnoreCase(line, "ApplicationContext failure threshold")
                                || containsIgnoreCase(line, "Failed to load ApplicationContext"))
                        .findFirst())
                .or(() -> testFailures.stream()
                        .map(this::failureDescription)
                        .filter(StringUtils::hasText)
                        .findFirst())
                .orElseGet(() -> firstNonBlankLine(text));
    }

    private String rootCauseType( // NOPMD - existing complexity baseline
            String text, List<SurefireTestFailure> testFailures) {
        if (containsIgnoreCase(text, "ryuk")
                || containsIgnoreCase(text, "Container startup failed")
                || containsIgnoreCase(text, "dockerjava")
                || containsIgnoreCase(text, "oscap")
                || containsIgnoreCase(text, "bash: not found")) {
            return "testcontainers_container_startup";
        }
        if (containsIgnoreCase(text, "ApplicationContext failure threshold")) {
            return "application_context_cascade";
        }
        if (containsIgnoreCase(text, "Failed to load ApplicationContext")) {
            return "application_context_load";
        }
        if (containsIgnoreCase(text, "<<< FAILURE!") || !testFailures.isEmpty()) {
            return "test_assertion_failure";
        }
        return "test_error";
    }

    private boolean isInfrastructure(String text) {
        return containsIgnoreCase(text, "testcontainers")
                || containsIgnoreCase(text, "ryuk")
                || containsIgnoreCase(text, "dockerjava")
                || containsIgnoreCase(text, "Container startup failed")
                || containsIgnoreCase(text, "oscap")
                || containsIgnoreCase(text, "bash: not found");
    }

    private boolean interesting(String line) {
        return containsIgnoreCase(line, "Tests run:")
                || containsIgnoreCase(line, "<<< FAILURE!")
                || containsIgnoreCase(line, "<<< ERROR!")
                || containsIgnoreCase(line, "Failed to load ApplicationContext")
                || containsIgnoreCase(line, "ApplicationContext failure threshold")
                || containsIgnoreCase(line, "Container startup failed")
                || containsIgnoreCase(line, "Could not create/start container")
                || containsIgnoreCase(line, "Status 500")
                || containsIgnoreCase(line, "oscap")
                || containsIgnoreCase(line, "bash: not found")
                || containsIgnoreCase(line, "ryuk")
                || containsIgnoreCase(line, "dockerjava")
                || containsIgnoreCase(line, "Caused by:");
    }

    private List<SurefireTestFailure> textTestFailures(String text) { // NOPMD - existing complexity baseline
        List<SurefireTestFailure> result = new ArrayList<>();
        String currentMethod = null;
        String currentKind = null;
        String exceptionType = null;
        String message = null;
        String sourceLocation = null;
        for (String line : text.split("\\R")) {
            Matcher caseMatcher = TXT_TEST_CASE.matcher(line);
            if (caseMatcher.matches()) {
                if (currentMethod != null) {
                    result.add(new SurefireTestFailure(
                            currentMethod, currentKind, exceptionType, message, sourceLocation));
                }
                currentMethod = caseMatcher.group(1);
                currentKind = caseMatcher.group(2).toLowerCase();
                exceptionType = null;
                message = null;
                sourceLocation = null;
                continue;
            }
            if (currentMethod == null) {
                continue;
            }
            if (exceptionType == null) {
                Matcher exceptionMatcher = EXCEPTION.matcher(line.strip());
                if (exceptionMatcher.matches()) {
                    exceptionType = exceptionMatcher.group(1);
                    message = trim(exceptionMatcher.group(2));
                }
            }
            if (sourceLocation == null) {
                Matcher frameMatcher = STACK_FRAME.matcher(line);
                if (frameMatcher.matches()) {
                    sourceLocation = frameMatcher.group(1);
                }
            }
        }
        if (currentMethod != null) {
            result.add(new SurefireTestFailure(currentMethod, currentKind, exceptionType, message, sourceLocation));
        }
        return result.stream().limit(MAX_TEST_FAILURES).toList();
    }

    private List<SurefireTestFailure> xmlTestFailures(String text) { // NOPMD - existing complexity baseline
        List<SurefireTestFailure> result = new ArrayList<>();
        try {
            NodeList testCases = parseXml(text).getElementsByTagName("testcase");
            for (int i = 0; i < testCases.getLength() && result.size() < MAX_TEST_FAILURES; i++) {
                Node node = testCases.item(i);
                if (!(node instanceof Element testCase)) {
                    continue;
                }
                Element detail = firstChild(testCase, "failure");
                String kind = "failure";
                if (detail == null) {
                    detail = firstChild(testCase, "error");
                    kind = "error";
                }
                if (detail == null) {
                    continue;
                }
                String stack = detail.getTextContent();
                String exceptionType = textOrNull(detail.getAttribute("type"));
                String message = trim(detail.getAttribute("message"));
                if (!StringUtils.hasText(message)) {
                    Matcher exception = firstException(stack);
                    message = exception == null ? null : trim(exception.group(2));
                    if (!StringUtils.hasText(exceptionType) && exception != null) {
                        exceptionType = exception.group(1);
                    }
                }
                result.add(new SurefireTestFailure(
                        textOrNull(testCase.getAttribute("name")),
                        kind,
                        exceptionType,
                        message,
                        firstSourceLocation(stack)));
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.copyOf(result);
    }

    private Element firstChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private Document parseXml(String text) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private Integer attributeInt(Element element, String name) {
        String value = element.getAttribute(name);
        return StringUtils.hasText(value) ? Integer.valueOf(value) : null;
    }

    private Matcher firstException(String text) {
        for (String line : text == null ? new String[0] : text.split("\\R")) {
            Matcher matcher = EXCEPTION.matcher(line.strip());
            if (matcher.matches()) {
                return matcher;
            }
        }
        return null;
    }

    private String firstSourceLocation(String text) {
        for (String line : text == null ? new String[0] : text.split("\\R")) {
            Matcher matcher = STACK_FRAME.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private String failureDescription(SurefireTestFailure failure) {
        String prefix = StringUtils.hasText(failure.methodName()) ? failure.methodName() + ": " : "";
        String type = StringUtils.hasText(failure.exceptionType()) ? failure.exceptionType() + ": " : "";
        return (prefix + type + (failure.message() == null ? "" : failure.message())).strip();
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        return normalized.length() > MAX_MESSAGE_LENGTH
                ? normalized.substring(0, MAX_MESSAGE_LENGTH) + "..."
                : normalized;
    }

    private String textOrNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private boolean isXml(String text) {
        return text.stripLeading().startsWith("<?xml") || text.stripLeading().startsWith("<testsuite");
    }

    private String firstNonBlankLine(String text) {
        for (String line : text.split("\\R")) {
            if (StringUtils.hasText(line)) {
                return line.strip();
            }
        }
        return null;
    }

    private record Counts(Integer testsRun, Integer failures, Integer errors, Integer skipped) {}
}
