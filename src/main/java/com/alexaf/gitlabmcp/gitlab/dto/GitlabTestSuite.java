package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitlabTestSuite(
        String name,
        @JsonProperty("total_time") Double totalTime,
        @JsonProperty("total_count") Integer totalCount,
        @JsonProperty("success_count") Integer successCount,
        @JsonProperty("failed_count") Integer failedCount,
        @JsonProperty("skipped_count") Integer skippedCount,
        @JsonProperty("error_count") Integer errorCount,
        @JsonProperty("test_cases") List<GitlabTestCase> testCases
) {

    public GitlabTestSuite {
        testCases = testCases == null ? List.of() : List.copyOf(testCases);
    }
}
