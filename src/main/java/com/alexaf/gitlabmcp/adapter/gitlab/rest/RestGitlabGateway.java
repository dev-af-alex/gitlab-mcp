package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.domain.GitlabPage;
import com.alexaf.gitlabmcp.domain.GitlabServerInfo;
import com.alexaf.gitlabmcp.domain.MergeRequestQuery;
import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;
import com.alexaf.gitlabmcp.gitlab.dto.Commit;
import com.alexaf.gitlabmcp.gitlab.dto.CurrentUser;
import com.alexaf.gitlabmcp.gitlab.dto.Discussion;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.MergeRequest;
import com.alexaf.gitlabmcp.gitlab.dto.MergeRequestChanges;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.alexaf.gitlabmcp.gitlab.dto.Project;
import com.alexaf.gitlabmcp.port.GitlabGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RestGitlabGateway implements GitlabGateway {

    private final GitlabApiClient gitlab;
    private final GitlabServerInfoProvider serverInfoProvider;
    private final MergeRequestDiffProvider mergeRequestDiffProvider;
    private final ArtifactIndexProvider artifactIndexProvider;

    @Autowired
    public RestGitlabGateway(
            GitlabApiClient gitlab,
            GitlabServerInfoProvider serverInfoProvider,
            MergeRequestDiffProvider mergeRequestDiffProvider,
            ArtifactIndexProvider artifactIndexProvider
    ) {
        this.gitlab = gitlab;
        this.serverInfoProvider = serverInfoProvider;
        this.mergeRequestDiffProvider = mergeRequestDiffProvider;
        this.artifactIndexProvider = artifactIndexProvider;
    }

    public RestGitlabGateway(GitlabApiClient gitlab) {
        GitlabServerInfoProvider serverInfoProvider =
                new GitlabServerInfoProvider(gitlab, new GitlabCapabilityResolver());
        this.gitlab = gitlab;
        this.serverInfoProvider = serverInfoProvider;
        this.mergeRequestDiffProvider = new MergeRequestDiffProvider(gitlab, serverInfoProvider);
        this.artifactIndexProvider = new ArtifactIndexProvider(gitlab, serverInfoProvider);
    }

    @Override
    public GitlabServerInfo getServerInfo() {
        return serverInfoProvider.get();
    }

    @Override
    public CurrentUser getCurrentUser() {
        return gitlab.getObject("/user", CurrentUser.class);
    }

    @Override
    public List<Project> searchProjects(String search, GitlabPageRequest page) {
        return gitlab.getList("/projects", Project.class,
                gitlab.param("search", search),
                gitlab.param("membership", true),
                gitlab.param("page", page(page)),
                gitlab.param("per_page", perPage(page)));
    }

    @Override
    public Project getProject(String projectId) {
        return gitlab.getObject("/projects/" + projectPath(projectId), Project.class);
    }

    @Override
    public List<MergeRequest> listMergeRequests(String projectId, MergeRequestQuery query) {
        return gitlab.getList(projectApi(projectId) + "/merge_requests", MergeRequest.class,
                gitlab.param("state", gitlab.mergeRequestState(query.state())),
                gitlab.param("search", query.search()),
                gitlab.param("source_branch", query.sourceBranch()),
                gitlab.param("target_branch", query.targetBranch()),
                gitlab.param("author_username", query.authorUsername()),
                gitlab.param("reviewer_username", query.reviewerUsername()),
                gitlab.param("order_by", "updated_at"),
                gitlab.param("sort", "desc"),
                gitlab.param("page", page(query.page())),
                gitlab.param("per_page", perPage(query.page())));
    }

    @Override
    public MergeRequest getMergeRequest(String projectId, String mergeRequestIid) {
        return gitlab.getObject(mergeRequestApi(projectId, mergeRequestIid), MergeRequest.class);
    }

    @Override
    public MergeRequestChanges getMergeRequestChanges(String projectId, String mergeRequestIid) {
        return mergeRequestDiffProvider.get(mergeRequestApi(projectId, mergeRequestIid));
    }

    @Override
    public List<Commit> listMergeRequestCommits(
            String projectId,
            String mergeRequestIid,
            GitlabPageRequest page
    ) {
        return gitlab.getList(mergeRequestApi(projectId, mergeRequestIid) + "/commits", Commit.class,
                gitlab.param("page", page(page)),
                gitlab.param("per_page", perPage(page)));
    }

    @Override
    public List<Discussion> listMergeRequestDiscussions(
            String projectId,
            String mergeRequestIid,
            GitlabPageRequest page
    ) {
        return gitlab.getList(mergeRequestApi(projectId, mergeRequestIid) + "/discussions", Discussion.class,
                gitlab.param("page", page(page)),
                gitlab.param("per_page", perPage(page)));
    }

    @Override
    public List<Pipeline> listMergeRequestPipelines(
            String projectId,
            String mergeRequestIid,
            GitlabPageRequest page
    ) {
        return gitlab.getList(mergeRequestApi(projectId, mergeRequestIid) + "/pipelines", Pipeline.class,
                gitlab.param("page", page(page)),
                gitlab.param("per_page", perPage(page)));
    }

    @Override
    public Pipeline getPipeline(String projectId, String pipelineId) {
        long id = gitlab.pipelineId(pipelineId);
        return gitlab.getObject(projectApi(projectId) + "/pipelines/" + id, Pipeline.class);
    }

    @Override
    public List<Job> listPipelineJobs(
            String projectId,
            String pipelineId,
            Boolean includeRetried,
            GitlabPageRequest page
    ) {
        long id = gitlab.pipelineId(pipelineId);
        return gitlab.getList(projectApi(projectId) + "/pipelines/" + id + "/jobs", Job.class,
                gitlab.param("include_retried", includeRetried),
                gitlab.param("page", page(page)),
                gitlab.param("per_page", perPage(page)));
    }

    @Override
    public GitlabPage<Job> getPipelineJobs(
            String projectId,
            String pipelineId,
            Boolean includeRetried,
            int maxJobs
    ) {
        long id = gitlab.pipelineId(pipelineId);
        return gitlab.getAllPages(
                projectApi(projectId) + "/pipelines/" + id + "/jobs",
                Job.class,
                maxJobs,
                gitlab.param("include_retried", includeRetried),
                gitlab.param("page", 1),
                gitlab.param("per_page", 100));
    }

    @Override
    public Job getJob(String projectId, String jobId) {
        long id = gitlab.jobId(jobId);
        return gitlab.getObject(projectApi(projectId) + "/jobs/" + id, Job.class);
    }

    @Override
    public String getJobTraceTail(String projectId, String jobId, Integer maxBytes) {
        long id = gitlab.jobId(jobId);
        return gitlab.getTailText(projectApi(projectId) + "/jobs/" + id + "/trace", maxBytes);
    }

    @Override
    public List<ArtifactFile> listJobArtifacts(
            String projectId,
            String jobId,
            String path,
            Boolean recursive,
            GitlabPageRequest page
    ) {
        long id = gitlab.jobId(jobId);
        return artifactIndexProvider.list(
                projectApi(projectId) + "/jobs/" + id,
                path,
                recursive,
                page);
    }

    @Override
    public List<ArtifactFile> findJobArtifactFiles(
            String projectId,
            String jobId,
            String pattern,
            Boolean regex,
            GitlabPageRequest page
    ) {
        long id = gitlab.jobId(jobId);
        return artifactIndexProvider.find(
                projectApi(projectId) + "/jobs/" + id,
                pattern,
                regex,
                page);
    }

    @Override
    public String getJobArtifactFile(
            String projectId,
            String jobId,
            String artifactPath,
            Integer maxBytes
    ) {
        long id = gitlab.jobId(jobId);
        return gitlab.getLimitedText(projectApi(projectId) + "/jobs/" + id + "/artifacts/"
                + normalizeArtifactPath(artifactPath), maxBytes);
    }

    private String projectApi(String projectId) {
        return "/projects/" + projectPath(projectId);
    }

    private String projectPath(String projectId) {
        return gitlab.projectPath(projectId);
    }

    private String mergeRequestApi(String projectId, String mergeRequestIid) {
        long iid = gitlab.mergeRequestIid(mergeRequestIid);
        return projectApi(projectId) + "/merge_requests/" + iid;
    }

    private int page(GitlabPageRequest page) {
        return gitlab.page(pageValue(page));
    }

    private int perPage(GitlabPageRequest page) {
        return gitlab.perPage(perPageValue(page));
    }

    private Integer pageValue(GitlabPageRequest page) {
        return page == null ? null : page.page();
    }

    private Integer perPageValue(GitlabPageRequest page) {
        return page == null ? null : page.perPage();
    }

    private String normalizeArtifactPath(String artifactPath) {
        if (artifactPath == null || artifactPath.isBlank()) {
            throw new IllegalArgumentException("artifactPath must be set");
        }
        String result = artifactPath.trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        if (result.isBlank()) {
            throw new IllegalArgumentException("artifactPath must be set");
        }
        return result;
    }
}
