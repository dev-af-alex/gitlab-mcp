package com.alexaf.gitlabmcp.port;

import com.alexaf.gitlabmcp.domain.PipelineContext;

public interface PipelineContextCollector {

    PipelineContext collect(String projectId, String pipelineId, String mergeRequestIid);
}
