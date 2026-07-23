package com.alexaf.gitlabmcp.domain;

import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;

import java.util.List;

public record PipelineGraph(
        List<PipelineNode> nodes,
        List<PipelineEdge> edges,
        boolean truncated
) {

    public PipelineGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public static PipelineGraph root(String projectId, Pipeline pipeline) {
        return new PipelineGraph(List.of(new PipelineNode(projectId, pipeline, 0)), List.of(), false);
    }
}
