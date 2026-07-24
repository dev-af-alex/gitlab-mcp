package com.alexaf.gitlabmcp.domain;

import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;

public record PipelineNode(String projectId, Pipeline pipeline, int depth) {}
