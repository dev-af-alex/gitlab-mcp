package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.util.List;

import com.alexaf.gitlabmcp.gitlab.dto.MergeRequestChanges;

public record MrPipelineFailureAnalysis(
        PipelineDiagnosticsResult pipelineDiagnostics,
        MergeRequestChanges mergeRequest,
        List<String> changedFiles,
        List<String> likelyRelevantChangedFiles,
        List<String> recommendedNextSteps) {}
