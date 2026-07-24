package com.alexaf.gitlabmcp.adapter.analysis.junit;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.alexaf.gitlabmcp.domain.Confidence;
import com.alexaf.gitlabmcp.domain.Evidence;
import com.alexaf.gitlabmcp.domain.Finding;
import com.alexaf.gitlabmcp.domain.FindingCategory;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.port.FailureAnalyzer;

@Component
public class JunitXmlFailureAnalyzer implements FailureAnalyzer {

    private static final int MAX_FINDINGS = 100;

    @Override
    public String id() {
        return "junit-xml";
    }

    @Override
    public int priority() {
        return 300;
    }

    @Override
    public boolean supports(PipelineContext context) {
        return !context.junitReports().isEmpty();
    }

    @Override
    public List<Finding> analyze(PipelineContext context) {
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, String> report : context.junitReports().entrySet()) {
            if (findings.size() >= MAX_FINDINGS) {
                break;
            }
            try {
                analyzeReport(report.getKey(), report.getValue(), findings);
            } catch (Exception e) {
                findings.add(new Finding(
                        FindingCategory.UNKNOWN,
                        toolchain(report.getKey()),
                        Confidence.LOW,
                        "Unable to parse JUnit report " + report.getKey(),
                        List.of(new Evidence(
                                "junit-xml",
                                null,
                                report.getKey(),
                                e.getClass().getSimpleName() + ": " + e.getMessage())),
                        List.of("Validate that the artifact contains well-formed JUnit XML.")));
            }
        }
        return List.copyOf(findings);
    }

    private void analyzeReport(String path, String xml, List<Finding> findings) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        var documentBuilder = factory.newDocumentBuilder();
        documentBuilder.setErrorHandler(new DefaultHandler() {
            @Override
            public void error(SAXParseException exception) throws SAXParseException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXParseException {
                throw exception;
            }
        });
        Element root =
                documentBuilder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
        NodeList testCases = root.getElementsByTagName("testcase");
        for (int index = 0; index < testCases.getLength() && findings.size() < MAX_FINDINGS; index++) {
            Element testCase = (Element) testCases.item(index);
            Element failure = firstChild(testCase, "failure", "error");
            if (failure == null) {
                continue;
            }
            findings.add(finding(path, testCase, failure));
        }
    }

    private Finding finding(String path, Element testCase, Element failure) {
        String className = testCase.getAttribute("classname");
        String testName = testCase.getAttribute("name");
        String qualifiedName = StringUtils.hasText(className) ? className + "#" + testName : testName;
        String type = failure.getAttribute("type");
        String message = failure.getAttribute("message");
        String details = compact(failure.getTextContent());
        List<Evidence> evidence = new ArrayList<>();
        if (StringUtils.hasText(type) || StringUtils.hasText(message)) {
            evidence.add(new Evidence(
                    "junit-xml",
                    null,
                    path,
                    (type + ": " + message).replaceAll("^:\\s*", "").strip()));
        }
        if (StringUtils.hasText(details)) {
            evidence.add(new Evidence("junit-xml", null, path, details));
        }
        return new Finding(
                FindingCategory.TEST,
                toolchain(path),
                Confidence.HIGH,
                "Test " + qualifiedName + " " + failure.getTagName(),
                evidence,
                List.of("Inspect the failing test and its JUnit stack trace."));
    }

    private Element firstChild(Element parent, String... names) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            for (String name : names) {
                if (name.equals(node.getNodeName())) {
                    return (Element) node;
                }
            }
        }
        return null;
    }

    private String toolchain(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        if (normalized.contains("jest") || normalized.contains("junit-js")) {
            return "jest";
        }
        if (normalized.contains("pytest") || normalized.contains("python")) {
            return "pytest";
        }
        if (normalized.contains("surefire")) {
            return "maven";
        }
        if (normalized.contains("test-results") || normalized.contains("gradle")) {
            return "gradle";
        }
        return "junit";
    }

    private String compact(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").strip();
        return compact.length() <= 2_000 ? compact : compact.substring(0, 2_000) + " [truncated]";
    }
}
