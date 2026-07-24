package com.alexaf.gitlabmcp.port;

import com.alexaf.gitlabmcp.domain.PipelineCollectionOptions;
import com.alexaf.gitlabmcp.domain.PipelineContext;

public interface PipelineContextCollector {

    default PipelineContext collect(String projectId, String pipelineId, String mergeRequestIid) {
        return collect(projectId, pipelineId, mergeRequestIid, PipelineCollectionOptions.metadataOnly());
    }

    PipelineContext collect(
            String projectId, String pipelineId, String mergeRequestIid, PipelineCollectionOptions options);
}
