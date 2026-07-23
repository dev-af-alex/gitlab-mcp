package com.alexaf.gitlabmcp.port;

import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.domain.MergeRequestQuery;
import com.alexaf.gitlabmcp.domain.GitlabServerInfo;
import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;
import com.alexaf.gitlabmcp.gitlab.dto.Commit;
import com.alexaf.gitlabmcp.gitlab.dto.CurrentUser;
import com.alexaf.gitlabmcp.gitlab.dto.Discussion;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.MergeRequest;
import com.alexaf.gitlabmcp.gitlab.dto.MergeRequestChanges;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.alexaf.gitlabmcp.gitlab.dto.Project;

import java.util.List;

public interface GitlabGateway {

    GitlabServerInfo getServerInfo();

    CurrentUser getCurrentUser();

    List<Project> searchProjects(String search, GitlabPageRequest page);

    Project getProject(String projectId);

    List<MergeRequest> listMergeRequests(String projectId, MergeRequestQuery query);

    MergeRequest getMergeRequest(String projectId, String mergeRequestIid);

    MergeRequestChanges getMergeRequestChanges(String projectId, String mergeRequestIid);

    List<Commit> listMergeRequestCommits(
            String projectId,
            String mergeRequestIid,
            GitlabPageRequest page
    );

    List<Discussion> listMergeRequestDiscussions(
            String projectId,
            String mergeRequestIid,
            GitlabPageRequest page
    );

    List<Pipeline> listMergeRequestPipelines(
            String projectId,
            String mergeRequestIid,
            GitlabPageRequest page
    );

    Pipeline getPipeline(String projectId, String pipelineId);

    List<Job> listPipelineJobs(
            String projectId,
            String pipelineId,
            Boolean includeRetried,
            GitlabPageRequest page
    );

    Job getJob(String projectId, String jobId);

    String getJobTraceTail(String projectId, String jobId, Integer maxBytes);

    List<ArtifactFile> listJobArtifacts(
            String projectId,
            String jobId,
            String path,
            Boolean recursive,
            GitlabPageRequest page
    );

    List<ArtifactFile> findJobArtifactFiles(
            String projectId,
            String jobId,
            String pattern,
            Boolean regex,
            GitlabPageRequest page
    );

    String getJobArtifactFile(
            String projectId,
            String jobId,
            String artifactPath,
            Integer maxBytes
    );
}
