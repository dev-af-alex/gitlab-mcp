package com.alexaf.gitlabmcp.gitlab.diagnostics;

import com.alexaf.gitlabmcp.gitlab.dto.MergeRequestChanges;

import java.util.List;

public record MrPipelineFailureAnalysis(
        PipelineDiagnosticsResult pipelineDiagnostics,
        MergeRequestChanges mergeRequest,
        List<String> changedFiles,
        List<String> likelyRelevantChangedFiles,
        List<String> recommendedNextSteps
) {
}
