package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import com.alexaf.gitlabmcp.domain.GitlabCapability;
import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabNotFoundException;
import com.alexaf.gitlabmcp.gitlab.dto.FileChange;
import com.alexaf.gitlabmcp.gitlab.dto.MergeRequest;
import com.alexaf.gitlabmcp.gitlab.dto.MergeRequestChanges;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MergeRequestDiffProvider {

    private static final int MAX_DIFF_FILES = 1_000;

    private final GitlabApiClient gitlab;
    private final GitlabServerInfoProvider serverInfoProvider;

    public MergeRequestDiffProvider(
            GitlabApiClient gitlab,
            GitlabServerInfoProvider serverInfoProvider
    ) {
        this.gitlab = gitlab;
        this.serverInfoProvider = serverInfoProvider;
    }

    public MergeRequestChanges get(String mergeRequestApi) {
        if (!serverInfoProvider.get().capabilities().contains(GitlabCapability.MERGE_REQUEST_DIFFS)) {
            return legacyChanges(mergeRequestApi);
        }
        try {
            List<FileChange> diffs = gitlab.getAllPages(
                    mergeRequestApi + "/diffs",
                    FileChange.class,
                    MAX_DIFF_FILES,
                    gitlab.param("page", 1),
                    gitlab.param("per_page", 100)).items();
            MergeRequest mergeRequest = gitlab.getObject(mergeRequestApi, MergeRequest.class);
            return changes(mergeRequest, diffs);
        } catch (GitlabNotFoundException unsupportedEndpoint) {
            return legacyChanges(mergeRequestApi);
        }
    }

    private MergeRequestChanges legacyChanges(String mergeRequestApi) {
        return gitlab.getObject(mergeRequestApi + "/changes", MergeRequestChanges.class);
    }

    private MergeRequestChanges changes(MergeRequest mergeRequest, List<FileChange> diffs) {
        return new MergeRequestChanges(
                mergeRequest.id(),
                mergeRequest.iid(),
                mergeRequest.projectId(),
                mergeRequest.title(),
                mergeRequest.description(),
                mergeRequest.state(),
                mergeRequest.targetBranch(),
                mergeRequest.sourceBranch(),
                mergeRequest.webUrl(),
                mergeRequest.diffRefs(),
                List.copyOf(diffs));
    }
}
