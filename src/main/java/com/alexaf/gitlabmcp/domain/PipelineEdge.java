package com.alexaf.gitlabmcp.domain;

public record PipelineEdge(
        String sourceProjectId,
        Long sourcePipelineId,
        String targetProjectId,
        Long targetPipelineId,
        Long bridgeJobId,
        String bridgeJobName) {}
