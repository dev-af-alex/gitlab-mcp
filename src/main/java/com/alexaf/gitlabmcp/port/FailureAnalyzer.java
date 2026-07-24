package com.alexaf.gitlabmcp.port;

import java.util.List;

import com.alexaf.gitlabmcp.domain.Finding;
import com.alexaf.gitlabmcp.domain.PipelineContext;

public interface FailureAnalyzer {

    String id();

    int priority();

    boolean supports(PipelineContext context);

    List<Finding> analyze(PipelineContext context);
}
