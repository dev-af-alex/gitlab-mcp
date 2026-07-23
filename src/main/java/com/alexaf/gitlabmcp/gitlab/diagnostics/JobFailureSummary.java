package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.util.List;

public record JobFailureSummary(
        Long jobId,
        String jobName,
        String jobStatus,
        String failureReason,
        String webUrl,
        MavenFailureSummary maven,
        LogMatchResult importantTraceMatches,
        RootCauseSummary primaryCause,
        List<SurefireReportInsight> surefireReports,
        List<String> contextCascadeClasses
) {

    public JobFailureSummary(
            Long jobId,
            String jobName,
            String jobStatus,
            String failureReason,
            String webUrl,
            MavenFailureSummary maven,
            LogMatchResult importantTraceMatches,
            RootCauseSummary primaryCause,
            List<SurefireReportInsight> surefireReports) {
        this(jobId, jobName, jobStatus, failureReason, webUrl, maven, importantTraceMatches,
                primaryCause, surefireReports, List.of());
    }
}
