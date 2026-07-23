package com.alexaf.gitlabmcp.port;

import com.alexaf.gitlabmcp.domain.Finding;
import com.alexaf.gitlabmcp.domain.PipelineContext;

import java.util.List;

public interface FailureAnalyzer {

    String id();

    int priority();

    boolean supports(PipelineContext context);

    List<Finding> analyze(PipelineContext context);
}
