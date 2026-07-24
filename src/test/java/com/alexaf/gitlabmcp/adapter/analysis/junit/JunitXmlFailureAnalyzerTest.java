package com.alexaf.gitlabmcp.adapter.analysis.junit;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alexaf.gitlabmcp.domain.Confidence;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;

import static org.assertj.core.api.Assertions.assertThat;

class JunitXmlFailureAnalyzerTest {

    private final JunitXmlFailureAnalyzer analyzer = new JunitXmlFailureAnalyzer();

    @Test
    void analyzesJestJUnitReportWithoutJavaSpecificAssumptions() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites tests="2" failures="1">
                  <testsuite name="checkout">
                    <testcase classname="Checkout API" name="returns 200">
                      <failure type="AssertionError" message="Expected 200, received 500">
                        at checkout.test.ts:42:7
                      </failure>
                    </testcase>
                    <testcase classname="Checkout API" name="returns items"/>
                  </testsuite>
                </testsuites>
                """;
        PipelineContext context = context(Map.of("reports/jest-junit.xml", xml));

        assertThat(analyzer.analyze(context)).singleElement().satisfies(finding -> {
            assertThat(finding.toolchain()).isEqualTo("jest");
            assertThat(finding.confidence()).isEqualTo(Confidence.HIGH);
            assertThat(finding.summary()).contains("Checkout API#returns 200");
            assertThat(finding.evidence())
                    .extracting(evidence -> evidence.message())
                    .anyMatch(message -> message.contains("received 500"));
        });
    }

    @Test
    void rejectsExternalEntities() {
        String xml = """
                <!DOCTYPE testsuite [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <testsuite><testcase name="unsafe"><failure>&secret;</failure></testcase></testsuite>
                """;
        PipelineContext context = context(Map.of("report.xml", xml));

        assertThat(analyzer.analyze(context)).singleElement().satisfies(finding -> {
            assertThat(finding.confidence()).isEqualTo(Confidence.LOW);
            assertThat(finding.summary()).contains("Unable to parse");
        });
    }

    private PipelineContext context(Map<String, String> reports) {
        Pipeline pipeline =
                new Pipeline(42L, 1L, 1L, "sha", "main", "failed", "push", null, null, null, null, 1L, 1L, null);
        return new PipelineContext(pipeline, List.of(), false, 0).withExecutionData(Map.of(), reports);
    }
}
