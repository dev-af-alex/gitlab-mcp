package com.alexaf.gitlabmcp.tool.gitlab;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.alexaf.gitlabmcp.application.JsonResponseWriter;
import com.alexaf.gitlabmcp.gitlab.diagnostics.PipelineDiagnosticsService;

@Component
public class GitlabDiagnosticsTools {

    private final JsonResponseWriter responseWriter;
    private final PipelineDiagnosticsService diagnosticsService;

    public GitlabDiagnosticsTools(JsonResponseWriter responseWriter, PipelineDiagnosticsService diagnosticsService) {
        this.responseWriter = responseWriter;
        this.diagnosticsService = diagnosticsService;
    }

    @McpTool(
            name = "gitlab_get_job_trace_matches",
            description =
                    "Search the redacted tail of a GitLab job trace and return matching lines with context. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getJobTraceMatches(
            @McpToolParam(description = "Project id or full path.") String projectId,
            @McpToolParam(description = "Job id or GitLab job URL.") String jobId,
            @McpToolParam(description = "Text or regex pattern to search in the trace tail.") String pattern,
            @McpToolParam(description = "Treat pattern as Java regex. Defaults to false.", required = false)
                    Boolean regex,
            @McpToolParam(description = "Context lines before each match. Defaults to 3.", required = false)
                    Integer contextBefore,
            @McpToolParam(description = "Context lines after each match. Defaults to 5.", required = false)
                    Integer contextAfter,
            @McpToolParam(description = "Maximum matches to return. Defaults to 20.", required = false)
                    Integer maxMatches,
            @McpToolParam(description = "Maximum trace tail bytes to inspect. Defaults to 60000.", required = false)
                    Integer maxBytes) {
        return responseWriter.write(diagnosticsService.traceMatches(
                projectId,
                jobId,
                pattern,
                regex,
                contextBefore,
                contextAfter,
                maxMatches,
                GitlabToolSupport.defaultMaxBytes(maxBytes)));
    }

    @McpTool(
            name = "gitlab_extract_job_failure_summary",
            description =
                    "Extract a compact Maven/Surefire-aware failure summary from a failed GitLab job trace and reports; full evidence is opt-in. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String extractJobFailureSummary(
            @McpToolParam(description = "Project id or full path.") String projectId,
            @McpToolParam(description = "Job id or GitLab job URL.") String jobId,
            @McpToolParam(description = "Maximum trace tail bytes to inspect. Defaults to 60000.", required = false)
                    Integer maxBytes,
            @McpToolParam(
                            description = "Include raw report evidence and trace context. Defaults to false.",
                            required = false)
                    Boolean includeDetails) {
        return responseWriter.write(diagnosticsService.extractJobFailureSummary(
                projectId, jobId, GitlabToolSupport.defaultMaxBytes(maxBytes), includeDetails));
    }

    @McpTool(
            name = "gitlab_analyze_job_surefire_reports",
            description =
                    "Analyze failed Maven test classes selected from a job log and read bounded Surefire .txt or TEST-*.xml reports. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String analyzeJobSurefireReports(
            @McpToolParam(description = "Project id or full path.") String projectId,
            @McpToolParam(description = "Job id or GitLab job URL.") String jobId,
            @McpToolParam(description = "Optional class name fragment to narrow reports.", required = false)
                    String classNamePattern,
            @McpToolParam(
                            description = "Maximum report files to inspect. Defaults to 5, capped at 20.",
                            required = false)
                    Integer maxReports) {
        return responseWriter.write(
                diagnosticsService.analyzeJobSurefireReports(projectId, jobId, classNamePattern, maxReports));
    }

    @McpTool(
            name = "gitlab_analyze_failed_pipeline",
            description =
                    "Analyze a failed GitLab pipeline with compact root cause, failed tests, and cascade grouping; full context and diffs are opt-in. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String analyzeFailedPipeline(
            @McpToolParam(description = "Project id or full path.") String projectId,
            @McpToolParam(
                            description = "Pipeline id or GitLab pipeline URL. Optional when mergeRequestIid is set.",
                            required = false)
                    String pipelineId,
            @McpToolParam(
                            description =
                                    "Merge request IID, !IID, or GitLab merge request URL. Used when pipelineId is not set.",
                            required = false)
                    String mergeRequestIid,
            @McpToolParam(description = "Whether to include failed job traces. Defaults to true.", required = false)
                    Boolean includeTraces,
            @McpToolParam(description = "Maximum trace bytes per failed job. Defaults to 60000.", required = false)
                    Integer maxTraceBytesPerJob,
            @McpToolParam(
                            description = "Whether to include raw trace text in the response. Defaults to false.",
                            required = false)
                    Boolean includeRawTraces,
            @McpToolParam(
                            description = "Whether to include likely useful artifact hints. Defaults to true.",
                            required = false)
                    Boolean includeArtifactHints,
            @McpToolParam(
                            description =
                                    "Include full trace contexts, report evidence, and merge-request diffs. Defaults to false.",
                            required = false)
                    Boolean includeDetails) {
        return responseWriter.write(diagnosticsService.analyze(
                projectId,
                pipelineId,
                mergeRequestIid,
                includeTraces,
                maxTraceBytesPerJob,
                includeRawTraces,
                includeArtifactHints,
                includeDetails));
    }

    @McpTool(
            name = "gitlab_analyze_mr_pipeline_failure",
            description =
                    "Analyze the latest failed merge request pipeline with compact root cause, failed tests, cascade grouping, and changed-file correlation; full context and diffs are opt-in. Read-only.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String analyzeMergeRequestPipelineFailure(
            @McpToolParam(description = "Project id or full path.") String projectId,
            @McpToolParam(description = "Merge request IID, !IID, or GitLab merge request URL.") String mergeRequestIid,
            @McpToolParam(
                            description = "Maximum trace bytes per failed job to inspect. Defaults to 60000.",
                            required = false)
                    Integer maxTraceBytesPerJob,
            @McpToolParam(
                            description = "Whether to include raw trace text in the response. Defaults to false.",
                            required = false)
                    Boolean includeRawTraces,
            @McpToolParam(
                            description =
                                    "Include full trace contexts, report evidence, and merge-request diffs. Defaults to false.",
                            required = false)
                    Boolean includeDetails) {
        return responseWriter.write(diagnosticsService.analyzeMergeRequestPipelineFailure(
                projectId,
                mergeRequestIid,
                GitlabToolSupport.defaultMaxBytes(maxTraceBytesPerJob),
                includeRawTraces,
                includeDetails));
    }
}
