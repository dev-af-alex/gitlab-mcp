package com.alexaf.gitlabmcp.domain;

import java.util.List;

import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;

public record PipelineGraph(List<PipelineNode> nodes, List<PipelineEdge> edges, boolean truncated) {

    public PipelineGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public static PipelineGraph root(String projectId, Pipeline pipeline) {
        return new PipelineGraph(List.of(new PipelineNode(projectId, pipeline, 0)), List.of(), false);
    }
}
