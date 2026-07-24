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
        List<String> contextCascadeClasses,
        List<String> warnings) {

    public JobFailureSummary {
        surefireReports = List.copyOf(surefireReports);
        contextCascadeClasses = List.copyOf(contextCascadeClasses);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public JobFailureSummary(
            Long jobId,
            String jobName,
            String jobStatus,
            String failureReason,
            String webUrl,
            MavenFailureSummary maven,
            LogMatchResult importantTraceMatches,
            RootCauseSummary primaryCause,
            List<SurefireReportInsight> surefireReports,
            List<String> contextCascadeClasses) {
        this(
                jobId,
                jobName,
                jobStatus,
                failureReason,
                webUrl,
                maven,
                importantTraceMatches,
                primaryCause,
                surefireReports,
                contextCascadeClasses,
                List.of());
    }

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
        this(
                jobId,
                jobName,
                jobStatus,
                failureReason,
                webUrl,
                maven,
                importantTraceMatches,
                primaryCause,
                surefireReports,
                List.of(),
                List.of());
    }
}
