package com.alexaf.gitlabmcp.domain;

import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.alexaf.gitlabmcp.gitlab.dto.GitlabTestReport;

import java.util.List;
import java.util.Map;

public record PipelineContext(
        Pipeline pipeline,
        List<Job> jobs,
        Map<Long, String> traces,
        Map<String, String> junitReports,
        GitlabTestReport testReport,
        boolean jobsTruncated,
        int totalJobsFetched
) {

    public PipelineContext {
        jobs = List.copyOf(jobs);
        traces = Map.copyOf(traces);
        junitReports = Map.copyOf(junitReports);
    }

    public PipelineContext(
            Pipeline pipeline,
            List<Job> jobs,
            boolean jobsTruncated,
            int totalJobsFetched
    ) {
        this(pipeline, jobs, Map.of(), Map.of(), null, jobsTruncated, totalJobsFetched);
    }

    public PipelineContext(
            Pipeline pipeline,
            List<Job> jobs,
            GitlabTestReport testReport,
            boolean jobsTruncated,
            int totalJobsFetched
    ) {
        this(pipeline, jobs, Map.of(), Map.of(), testReport, jobsTruncated, totalJobsFetched);
    }

    public PipelineContext withExecutionData(
            Map<Long, String> traces,
            Map<String, String> junitReports
    ) {
        return new PipelineContext(
                pipeline,
                jobs,
                traces,
                junitReports,
                testReport,
                jobsTruncated,
                totalJobsFetched);
    }
}
