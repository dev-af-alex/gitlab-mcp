package com.alexaf.gitlabmcp.domain;

public record PipelineCollectionOptions(
        boolean includeTraces,
        int maxTraceBytes,
        boolean includeArtifacts,
        int maxArtifactFilesPerJob,
        int maxJunitReports,
        int maxReportBytes) {

    public static PipelineCollectionOptions metadataOnly() {
        return new PipelineCollectionOptions(false, 60_000, false, 100, 20, 128_000);
    }
}
