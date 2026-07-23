package com.alexaf.gitlabmcp.gitlab.diagnostics;

import com.alexaf.gitlabmcp.adapter.gitlab.rest.RestGitlabGateway;
import com.alexaf.gitlabmcp.adapter.analysis.generic.GenericTraceFailureAnalyzer;
import com.alexaf.gitlabmcp.adapter.analysis.junit.GitlabTestReportAnalyzer;
import com.alexaf.gitlabmcp.adapter.analysis.junit.JunitXmlFailureAnalyzer;
import com.alexaf.gitlabmcp.adapter.analysis.maven.MavenTraceFailureAnalyzer;
import com.alexaf.gitlabmcp.application.pipeline.DefaultPipelineContextCollector;
import com.alexaf.gitlabmcp.application.pipeline.PipelineAnalysisEngine;
import com.alexaf.gitlabmcp.domain.PipelineAnalysis;
import com.alexaf.gitlabmcp.domain.PipelineCollectionOptions;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;
import com.alexaf.gitlabmcp.gitlab.dto.FileChange;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.MergeRequestChanges;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.alexaf.gitlabmcp.port.PipelineContextCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class PipelineDiagnosticsService {

    private static final int DEFAULT_MAX_TRACE_BYTES = 60_000;
    private static final int MAX_USEFUL_ARTIFACTS = 20;
    private static final int MAX_SUREFIRE_REPORTS = 20;
    private static final int MAX_SUREFIRE_REPORT_BYTES = 128_000;

    private final GitlabApiClient gitlab;
    private final PipelineContextCollector contextCollector;
    private final PipelineAnalysisEngine analysisEngine;
    private final TraceAnalyzer traceAnalyzer;
    private final MavenFailureAnalyzer mavenFailureAnalyzer;
    private final SurefireReportAnalyzer surefireReportAnalyzer;
    private final LogMatcher logMatcher;
    private final ArtifactHintDetector artifactHintDetector;

    @Autowired
    public PipelineDiagnosticsService(
            GitlabApiClient gitlab,
            PipelineContextCollector contextCollector,
            PipelineAnalysisEngine analysisEngine,
            TraceAnalyzer traceAnalyzer,
            MavenFailureAnalyzer mavenFailureAnalyzer,
            SurefireReportAnalyzer surefireReportAnalyzer,
            LogMatcher logMatcher,
            ArtifactHintDetector artifactHintDetector) {
        this.gitlab = gitlab;
        this.contextCollector = contextCollector;
        this.analysisEngine = analysisEngine;
        this.traceAnalyzer = traceAnalyzer;
        this.mavenFailureAnalyzer = mavenFailureAnalyzer;
        this.surefireReportAnalyzer = surefireReportAnalyzer;
        this.logMatcher = logMatcher;
        this.artifactHintDetector = artifactHintDetector;
    }

    public PipelineDiagnosticsService(
            GitlabApiClient gitlab,
            TraceAnalyzer traceAnalyzer,
            MavenFailureAnalyzer mavenFailureAnalyzer,
            SurefireReportAnalyzer surefireReportAnalyzer,
            LogMatcher logMatcher,
            ArtifactHintDetector artifactHintDetector
    ) {
        this(
                gitlab,
                new DefaultPipelineContextCollector(new RestGitlabGateway(gitlab), 500),
                new PipelineAnalysisEngine(List.of(
                        new GitlabTestReportAnalyzer(),
                        new JunitXmlFailureAnalyzer(),
                        new MavenTraceFailureAnalyzer(mavenFailureAnalyzer),
                        new GenericTraceFailureAnalyzer(traceAnalyzer))),
                traceAnalyzer,
                mavenFailureAnalyzer,
                surefireReportAnalyzer,
                logMatcher,
                artifactHintDetector);
    }

    private static String simpleName(String className) {
        if (!StringUtils.hasText(className)) {
            return "";
        }
        int index = className.lastIndexOf('.');
        return index >= 0 ? className.substring(index + 1) : className;
    }

    private static String reportClassKey(String path) {
        String name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        if (name.startsWith("TEST-")) {
            name = name.substring("TEST-".length());
        }
        if (name.endsWith(".txt") || name.endsWith(".xml")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    private static String artifactPath(String artifactPath) {
        String result = artifactPath;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private static String packagePath(String className) {
        if (!StringUtils.hasText(className) || !className.contains(".")) {
            return "";
        }
        return className.substring(0, className.lastIndexOf('.')).replace('.', '/');
    }

    public PipelineDiagnosticsResult analyze(
            String projectId,
            String pipelineId,
            String mergeRequestIid,
            Boolean includeTraces,
            Integer maxTraceBytesPerJob,
            Boolean includeRawTraces,
            Boolean includeArtifactHints) {
        return analyze(projectId, pipelineId, mergeRequestIid, includeTraces, maxTraceBytesPerJob,
                includeRawTraces, includeArtifactHints, false);
    }

    public PipelineDiagnosticsResult analyze(
            String projectId,
            String pipelineId,
            String mergeRequestIid,
            Boolean includeTraces,
            Integer maxTraceBytesPerJob,
            Boolean includeRawTraces,
        Boolean includeArtifactHints,
        Boolean includeDetails) {
        String projectPath = gitlab.projectPath(projectId);
        boolean tracesEnabled = includeTraces == null || includeTraces;
        boolean rawTracesEnabled = includeRawTraces != null && includeRawTraces;
        boolean artifactHintsEnabled = includeArtifactHints == null || includeArtifactHints;
        boolean detailsEnabled = includeDetails != null && includeDetails;
        int maxTraceBytes = maxTraceBytesPerJob == null || maxTraceBytesPerJob <= 0
                            ? DEFAULT_MAX_TRACE_BYTES
                            : maxTraceBytesPerJob;

        PipelineCollectionOptions collectionOptions = new PipelineCollectionOptions(
                tracesEnabled,
                maxTraceBytes,
                artifactHintsEnabled,
                100,
                MAX_SUREFIRE_REPORTS,
                MAX_SUREFIRE_REPORT_BYTES);
        PipelineContext context = contextCollector.collect(
                projectId,
                pipelineId,
                mergeRequestIid,
                collectionOptions);
        Pipeline pipeline = context.pipeline();
        List<Job> jobs = context.jobs();
        List<JobDiagnostic> failedJobs = jobs.stream()
                .filter(job -> "failed".equals(job.status()))
                .map(job -> diagnoseJob(
                        projectPath,
                        job,
                        context.traces().get(job.id()),
                        context.artifacts().get(job.id()),
                        rawTracesEnabled,
                        artifactHintsEnabled,
                        detailsEnabled))
                .toList();
        List<JobSummary> otherNotSuccessfulJobs = jobs.stream()
                .filter(job -> !"success".equals(job.status()) && !"failed".equals(job.status()))
                .map(this::summary)
                .toList();

        String warning = context.jobsTruncated()
                ? "Pipeline job inspection stopped after " + context.totalJobsFetched()
                        + " jobs because GITLAB_MAX_JOBS was reached."
                : null;
        PipelineAnalysis analysis = analysisEngine.analyze(context);
        return new PipelineDiagnosticsResult(
                pipeline,
                summary(pipeline, failedJobs, otherNotSuccessfulJobs),
                failedJobs,
                otherNotSuccessfulJobs,
                tracesEnabled,
                rawTracesEnabled,
                artifactHintsEnabled,
                warning,
                detailsEnabled,
                analysis.findings(),
                analysis.analyzers());
    }

    public JobFailureSummary extractJobFailureSummary(
            String projectId,
            String jobId,
            Integer maxTraceBytes,
            Boolean includeDetails) {
        String projectPath = gitlab.projectPath(projectId);
        long id = gitlab.jobId(jobId);
        Job job = gitlab.getObject("/projects/" + projectPath + "/jobs/" + id, Job.class);
        String trace = gitlab.getTailText("/projects/" + projectPath + "/jobs/" + id + "/trace",
                maxTraceBytes == null || maxTraceBytes <= 0 ? DEFAULT_MAX_TRACE_BYTES : maxTraceBytes);
        return failureSummary(projectPath, job, trace, includeDetails != null && includeDetails);
    }

    public List<SurefireReportInsight> analyzeJobSurefireReports(
            String projectId,
            String jobId,
            String classNamePattern,
            Integer maxReports) {
        String projectPath = gitlab.projectPath(projectId);
        long id = gitlab.jobId(jobId);
        int reportLimit = maxReports == null || maxReports <= 0 ? MAX_SUREFIRE_REPORTS : Math.min(maxReports, 20);
        if (StringUtils.hasText(classNamePattern)) {
            String classPattern = Pattern.quote(classNamePattern.trim());
            return analyzeSurefireReports(projectPath, id, List.of(
                    ".*" + classPattern + ".*\\.txt$",
                    ".*TEST-.*" + classPattern + ".*\\.xml$"), reportLimit);
        }
        return analyzeSurefireReports(projectPath, id,
                surefireReportPatterns(artifactMavenSummary(projectPath, id)), reportLimit);
    }

    public LogMatchResult traceMatches(
            String projectId,
            String jobId,
            String pattern,
            Boolean regex,
            Integer contextBefore,
            Integer contextAfter,
            Integer maxMatches,
            Integer maxTraceBytes) {
        String projectPath = gitlab.projectPath(projectId);
        long id = gitlab.jobId(jobId);
        String trace = gitlab.getTailText("/projects/" + projectPath + "/jobs/" + id + "/trace",
                maxTraceBytes == null || maxTraceBytes <= 0 ? DEFAULT_MAX_TRACE_BYTES : maxTraceBytes);
        return logMatcher.findMatches(trace, pattern, regex, contextBefore, contextAfter, maxMatches);
    }

    public MrPipelineFailureAnalysis analyzeMergeRequestPipelineFailure(
            String projectId,
            String mergeRequestIid,
            Integer maxTraceBytesPerJob,
            Boolean includeRawTraces,
            Boolean includeDetails) {
        PipelineDiagnosticsResult pipelineDiagnostics = analyze(
                projectId,
                null,
                mergeRequestIid,
                true,
                maxTraceBytesPerJob,
                includeRawTraces,
                true,
                includeDetails);
        String projectPath = gitlab.projectPath(projectId);
        long iid = gitlab.mergeRequestIid(mergeRequestIid);
        MergeRequestChanges changes = gitlab.getObject("/projects/" + projectPath + "/merge_requests/" + iid + "/changes",
                MergeRequestChanges.class);
        List<String> changedFiles = changes.changes() == null
                                    ? List.of()
                                    : changes.changes().stream().map(change -> change.newPath() != null ? change.newPath() : change.oldPath()).toList();
        MergeRequestChanges responseChanges = includeDetails != null && includeDetails
                                              ? changes
                                              : compactChanges(changes);
        return new MrPipelineFailureAnalysis(
                pipelineDiagnostics,
                responseChanges,
                changedFiles,
                likelyRelevantChangedFiles(changedFiles, pipelineDiagnostics.failedJobs()),
                recommendedNextSteps(pipelineDiagnostics));
    }

    private JobDiagnostic diagnoseJob(
            String projectPath,
            Job job,
            String trace,
            List<ArtifactFile> knownArtifacts,
            boolean includeRawTrace,
            boolean includeArtifactHints,
            boolean includeDetails) {
        TraceAnalysis analysis = traceAnalyzer.analyze(job, trace);
        JobFailureSummary failureSummary = failureSummary(projectPath, job, trace, includeDetails);
        RootCauseSummary primaryCause = failureSummary.primaryCause();
        String detectedCause = effectiveDetectedCause(analysis, primaryCause);
        return new JobDiagnostic(
                job.id(),
                job.name(),
                job.stage(),
                job.status(),
                job.failureReason(),
                job.webUrl(),
                detectedCause,
                analysis.confidence(),
                includeDetails ? analysis.evidence() : compactLines(analysis.evidence(), 6),
                failureSummary,
                includeArtifactHints
                ? (includeDetails
                ? usefulArtifacts(projectPath, job, knownArtifacts)
                : usefulArtifacts(projectPath, job, knownArtifacts).stream().limit(10).toList())
                : List.of(),
                includeRawTrace ? trace : null,
                trace != null && trace.contains("[truncated to "),
                analysis.nextSteps());
    }

    private List<String> usefulArtifacts(
            String projectPath,
            Job job,
            List<ArtifactFile> knownArtifacts
    ) {
        if (knownArtifacts != null) {
            return artifactHintDetector.usefulArtifacts(job, knownArtifacts).stream()
                    .limit(MAX_USEFUL_ARTIFACTS)
                    .toList();
        }
        List<ArtifactFile> artifactFiles ;
        try {
            artifactFiles = gitlab.listArtifactArchive(
                    "/projects/" + projectPath + "/jobs/" + job.id() + "/artifacts",
                    null,
                    true,
                    1,
                    100);
        } catch (IllegalArgumentException ignored) {
            return artifactHintDetector.usefulArtifacts(job, List.of());
        }
        return artifactHintDetector.usefulArtifacts(job, artifactFiles).stream()
                .limit(MAX_USEFUL_ARTIFACTS)
                .toList();
    }

    private JobFailureSummary failureSummary(String projectPath, Job job, String trace, boolean includeDetails) {
        MavenFailureSummary maven = mavenFailureAnalyzer.analyze(trace);
        maven = mavenFailureAnalyzer.merge(maven, artifactMavenSummary(projectPath, job.id()));
        List<SurefireReportInsight> surefireReports = maven.testFailureDetected()
                                                      ? analyzeSurefireReports(projectPath, job.id(), surefireReportPatterns(maven), MAX_SUREFIRE_REPORTS)
                                                      : List.of();
        LogMatchResult importantTraceMatches = logMatcher.importantMatches(trace);
        RootCauseSummary primaryCause = primaryCause(maven, surefireReports, importantTraceMatches);
        JobFailureSummary result = new JobFailureSummary(
                job.id(),
                job.name(),
                job.status(),
                job.failureReason(),
                job.webUrl(),
                maven,
                importantTraceMatches,
                primaryCause,
                surefireReports,
                contextCascadeClasses(maven, surefireReports));
        return includeDetails ? result : compactFailureSummary(result);
    }

    private JobFailureSummary compactFailureSummary(JobFailureSummary summary) {
        List<SurefireReportInsight> reports = summary.surefireReports().stream()
                .filter(report -> (report.failures() != null && report.failures() > 0)
                        || (report.errors() != null && report.errors() > 0)
                        || !report.testFailures().isEmpty()
                        || report.infrastructure())
                .map(SurefireReportInsight::compact)
                .toList();
        return new JobFailureSummary(
                summary.jobId(),
                summary.jobName(),
                summary.jobStatus(),
                summary.failureReason(),
                summary.webUrl(),
                summary.maven().compact(),
                summary.importantTraceMatches().compact(),
                summary.primaryCause() == null ? null : summary.primaryCause().compact(),
                reports,
                summary.contextCascadeClasses());
    }

    private List<String> contextCascadeClasses(
            MavenFailureSummary maven,
            List<SurefireReportInsight> reports) {
        Set<String> result = new LinkedHashSet<>();
        maven.errorTests().stream()
                .filter(MavenTestError::contextCascade)
                .map(MavenTestError::className)
                .filter(StringUtils::hasText)
                .forEach(result::add);
        reports.stream()
                .filter(SurefireReportInsight::contextCascade)
                .map(SurefireReportInsight::className)
                .filter(StringUtils::hasText)
                .forEach(result::add);
        return result.stream().limit(20).toList();
    }

    private String effectiveDetectedCause(TraceAnalysis analysis, RootCauseSummary primaryCause) {
        if (primaryCause == null) {
            return analysis.detectedCause();
        }
        if (primaryCause.infrastructure()) {
            return "Infrastructure failure: " + primaryCause.type();
        }
        if ("maven_test_compilation_failure".equals(primaryCause.type())) {
            return "Maven test compilation failure";
        }
        return analysis.detectedCause();
    }

    private List<String> compactLines(List<String> lines, int limit) {
        return lines.stream()
                .map(line -> line.replaceAll("\\u001B\\[[;\\d]*m", "")
                        .replaceAll("\\s+", " ").strip())
                .filter(StringUtils::hasText)
                .distinct()
                .limit(limit)
                .toList();
    }

    private List<String> likelyRelevantChangedFiles(List<String> changedFiles, List<JobDiagnostic> failedJobs) {
        if (failedJobs.stream().anyMatch(job -> job.failureSummary().primaryCause() != null
                && job.failureSummary().primaryCause().infrastructure())) {
            return List.of();
        }
        List<String> failureTokens = failedJobs.stream()
                .flatMap(job -> failureTokens(job.failureSummary()).stream())
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        List<String> relevant = changedFiles.stream()
                .filter(path -> failureTokens.stream().anyMatch(token -> path.toLowerCase(Locale.ROOT).contains(token)))
                .toList();
        return relevant;
    }

    private List<String> recommendedNextSteps(PipelineDiagnosticsResult pipelineDiagnostics) {
        List<String> result = new ArrayList<>();
        for (JobDiagnostic job : pipelineDiagnostics.failedJobs()) {
            RootCauseSummary primaryCause = job.failureSummary().primaryCause();
            if (primaryCause != null && primaryCause.infrastructure()) {
                result.add("Retry the pipeline or move it to a healthy runner.");
                result.add("Check Testcontainers/Ryuk image startup policy on CI.");
                result.add("Do not start code changes until infra failure is reproduced locally or rerun fails the same way.");
                return result;
            }
            if (job.failureSummary().maven().compilationFailureDetected()) {
                result.add("Fix the Maven test compilation error before investigating test assertions.");
                result.add("Inspect the reported source location, symbol, and test-scope dependencies.");
                return result;
            }
            if (job.failureSummary().maven().testFailureDetected()) {
                result.add("Inspect primaryCause and surefireReports before raw logs.");
                result.add("Use gitlab_analyze_job_surefire_reports when more test report detail is needed.");
                result.add("Compare failing test area with merge request changes.");
                return result;
            }
        }
        result.add("Use gitlab_get_job_trace_matches for targeted trace evidence.");
        return result;
    }

    private List<SurefireReportInsight> analyzeSurefireReports(
            String projectPath,
            Long jobId,
            List<String> patterns,
            int maxReports) {
        List<SurefireReportInsight> result = new ArrayList<>();
        Set<String> seenPaths = new LinkedHashSet<>();
        Set<String> seenReportClasses = new LinkedHashSet<>();
        for (String pattern : patterns) {
            if (result.size() >= maxReports) {
                break;
            }
            List<ArtifactFile> files;
            try {
                files = gitlab.findArtifactArchiveFiles(
                        "/projects/" + projectPath + "/jobs/" + jobId + "/artifacts",
                        pattern,
                        true,
                        1,
                        maxReports);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            for (ArtifactFile file : files) {
                if (result.size() >= maxReports
                        || !seenPaths.add(file.path())
                        || !seenReportClasses.add(reportClassKey(file.path()))) {
                    continue;
                }
                String text = gitlab.getLimitedText(
                        "/projects/" + projectPath + "/jobs/" + jobId + "/artifacts/" + artifactPath(file.path()),
                        MAX_SUREFIRE_REPORT_BYTES);
                result.add(surefireReportAnalyzer.analyze(file.path(), text));
            }
        }
        return List.copyOf(result);
    }

    private List<String> surefireReportPatterns(MavenFailureSummary maven) {
        List<String> classNames = mavenFailureAnalyzer.failedClassNames(maven).stream()
                .map(PipelineDiagnosticsService::simpleName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<String> result = new ArrayList<>();
        classNames.stream()
                .map(simpleName -> ".*" + Pattern.quote(simpleName) + "\\.txt$")
                .forEach(result::add);
        classNames.stream()
                .map(simpleName -> ".*TEST-.*" + Pattern.quote(simpleName) + "\\.xml$")
                .forEach(result::add);
        if (result.isEmpty()) {
            result.add(".*surefire-reports.*\\.(?:txt|xml)$");
        }
        return result;
    }

    private RootCauseSummary primaryCause(
            MavenFailureSummary maven,
            List<SurefireReportInsight> surefireReports,
            LogMatchResult importantTraceMatches) {
        if (maven.executionFailureDetected()) {
            boolean runnerFailure = maven.evidence().stream().anyMatch(line -> line.contains("Process Exit Code: 137")
                    || line.contains("forked VM terminated"));
            return new RootCauseSummary(
                    "maven_test_execution_failure",
                    null,
                    maven.evidence().stream()
                            .filter(line -> line.contains("Process Exit Code:")
                                    || line.contains("forked VM terminated")
                                    || line.contains("Crashed tests:"))
                            .findFirst()
                            .orElse(maven.detectedCause()),
                    runnerFailure,
                    runnerFailure ? "retry_pipeline_or_reduce_parallel_test_forks" : "inspect_surefire_fork_configuration",
                    "high",
                    maven.evidence());
        }
        if (maven.compilationFailureDetected()) {
            return new RootCauseSummary(
                    "maven_test_compilation_failure",
                    null,
                    maven.evidence().stream()
                            .filter(line -> line.contains("symbol:")
                                    || line.contains("location:")
                                    || line.contains("Compilation failure"))
                            .findFirst()
                            .orElse(maven.detectedCause()),
                    false,
                    "fix_test_compilation_before_inspecting_test_assertions",
                    "high",
                    maven.evidence());
        }
        SurefireReportInsight selected = surefireReports.stream()
                .filter(report -> report.infrastructure() && !report.contextCascade())
                .findFirst()
                .or(() -> surefireReports.stream().filter(report -> !report.contextCascade()).findFirst())
                .or(() -> surefireReports.stream().findFirst())
                .orElse(null);
        if (selected != null) {
            return new RootCauseSummary(
                    selected.rootCauseType(),
                    selected.className(),
                    selected.rootCauseMessage(),
                    selected.infrastructure(),
                    selected.infrastructure()
                    ? "retry_pipeline_or_fix_ci_runner"
                    : "inspect_test_report_and_related_code",
                    selected.infrastructure() ? "high" : "medium",
                    selected.evidence());
        }
        if (!maven.errorTests().isEmpty()) {
            MavenTestError error = maven.errorTests().getFirst();
            return new RootCauseSummary(
                    error.contextCascade() ? "application_context_cascade" : "maven_test_error",
                    error.className(),
                    error.errorType() + " " + error.message(),
                    false,
                    "inspect_surefire_report",
                    "medium",
                    maven.evidence());
        }
        if (!maven.failingTests().isEmpty()) {
            MavenTestFailure failure = maven.failingTests().getFirst();
            return new RootCauseSummary(
                    "maven_test_failure",
                    failure.className(),
                    failure.message(),
                    false,
                    "inspect_changed_code_and_test_expectation",
                    "high",
                    maven.evidence());
        }
        if (maven.testFailureDetected()) {
            return new RootCauseSummary(
                    "maven_test_failure",
                    null,
                    maven.detectedCause(),
                    false,
                    "inspect_surefire_reports",
                    maven.confidence(),
                    maven.evidence());
        }
        return new RootCauseSummary(
                "trace_failure",
                null,
                importantTraceMatches.matches().isEmpty() ? null : importantTraceMatches.matches().getFirst().text(),
                false,
                "inspect_trace_matches",
                "low",
                importantTraceMatches.matches().stream().map(LogMatch::text).limit(10).toList());
    }

    private List<String> failureTokens(JobFailureSummary summary) {
        List<String> result = new ArrayList<>();
        mavenFailureAnalyzer.failedClassNames(summary.maven()).stream()
                .flatMap(className -> Stream.of(
                        simpleName(className).replace("Test", ""),
                        packagePath(className)))
                .forEach(result::add);
        summary.surefireReports().stream()
                .map(SurefireReportInsight::className)
                .filter(StringUtils::hasText)
                .flatMap(className -> Stream.of(
                        simpleName(className).replace("Test", ""),
                        packagePath(className)))
                .forEach(result::add);
        return result;
    }

    private MavenFailureSummary artifactMavenSummary(String projectPath, Long jobId) {
        try {
            List<ArtifactFile> logs = gitlab.findArtifactArchiveFiles(
                    "/projects/" + projectPath + "/jobs/" + jobId + "/artifacts",
                    ".*\\.log$",
                    true,
                    1,
                    3);
            MavenFailureSummary result = null;
            for (ArtifactFile log : logs) {
                String text = gitlab.getTailText(
                        "/projects/" + projectPath + "/jobs/" + jobId + "/artifacts/" + artifactPath(log.path()),
                        DEFAULT_MAX_TRACE_BYTES);
                result = mavenFailureAnalyzer.merge(result, mavenFailureAnalyzer.analyze(text));
            }
            return result == null
                   ? noArtifactLog()
                   : result;
        } catch (IllegalArgumentException ignored) {
            return noArtifactLog();
        }
    }

    private MavenFailureSummary noArtifactLog() {
        return new MavenFailureSummary(false, false, "No artifact log", "unknown",
                null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private MergeRequestChanges compactChanges(MergeRequestChanges changes) {
        if (changes == null || changes.changes() == null) {
            return changes;
        }
        List<FileChange> compactChanges = changes.changes().stream()
                .map(change -> new FileChange(
                        change.oldPath(),
                        change.newPath(),
                        change.aMode(),
                        change.bMode(),
                        change.newFile(),
                        change.renamedFile(),
                        change.deletedFile(),
                        null))
                .toList();
        return new MergeRequestChanges(
                changes.id(),
                changes.iid(),
                changes.projectId(),
                changes.title(),
                null,
                changes.state(),
                changes.targetBranch(),
                changes.sourceBranch(),
                changes.webUrl(),
                changes.diffRefs(),
                compactChanges);
    }

    private JobSummary summary(Job job) {
        return new JobSummary(job.id(), job.name(), job.stage(), job.status(), job.failureReason(), job.webUrl());
    }

    private String summary(Pipeline pipeline, List<JobDiagnostic> failedJobs, List<JobSummary> otherNotSuccessfulJobs) {
        if (!failedJobs.isEmpty()) {
            return "Pipeline " + pipeline.id() + " failed in " + failedJobs.size() + " job(s): "
                    + failedJobs.stream().map(JobDiagnostic::name).toList();
        }
        if (!otherNotSuccessfulJobs.isEmpty()) {
            return "Pipeline " + pipeline.id() + " has no failed jobs in the inspected page, but has non-success jobs: "
                    + otherNotSuccessfulJobs.stream().map(JobSummary::name).toList();
        }
        return "Pipeline " + pipeline.id() + " has no failed jobs in the inspected page.";
    }
}
