package com.alexaf.gitlabmcp.domain;

import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;

import java.util.List;

public record PipelineContext(
        Pipeline pipeline,
        List<Job> jobs,
        boolean jobsTruncated,
        int totalJobsFetched
) {

    public PipelineContext {
        jobs = List.copyOf(jobs);
    }
}
