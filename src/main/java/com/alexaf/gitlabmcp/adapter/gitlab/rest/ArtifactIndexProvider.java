package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import com.alexaf.gitlabmcp.domain.GitlabCapability;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.gitlab.client.GitlabApiClient;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabNotFoundException;
import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class ArtifactIndexProvider {

    private static final int MAX_ARTIFACT_ENTRIES = 10_000;

    private final GitlabApiClient gitlab;
    private final GitlabServerInfoProvider serverInfoProvider;

    public ArtifactIndexProvider(
            GitlabApiClient gitlab,
            GitlabServerInfoProvider serverInfoProvider
    ) {
        this.gitlab = gitlab;
        this.serverInfoProvider = serverInfoProvider;
    }

    public List<ArtifactFile> list(
            String jobApi,
            String path,
            Boolean recursive,
            GitlabPageRequest page
    ) {
        if (!supportsArtifactTree()) {
            return legacyList(jobApi, path, recursive, page);
        }
        try {
            return gitlab.getList(jobApi + "/artifacts/tree", ArtifactFile.class,
                    gitlab.param("path", path),
                    gitlab.param("recursive", recursive),
                    gitlab.param("page", gitlab.page(pageValue(page))),
                    gitlab.param("per_page", gitlab.perPage(perPageValue(page))));
        } catch (GitlabNotFoundException unsupportedEndpoint) {
            return legacyList(jobApi, path, recursive, page);
        }
    }

    public List<ArtifactFile> find(
            String jobApi,
            String pattern,
            Boolean regex,
            GitlabPageRequest page
    ) {
        if (!supportsArtifactTree()) {
            return legacyFind(jobApi, pattern, regex, page);
        }
        try {
            Pattern compiled = compilePathPattern(pattern, regex);
            List<ArtifactFile> matches = gitlab.getAllPages(
                            jobApi + "/artifacts/tree",
                            ArtifactFile.class,
                            MAX_ARTIFACT_ENTRIES,
                            gitlab.param("recursive", true),
                            gitlab.param("page", 1),
                            gitlab.param("per_page", 100))
                    .items().stream()
                    .filter(artifact -> "file".equals(artifact.type()))
                    .filter(artifact -> compiled.matcher(artifact.path()).matches())
                    .toList();
            return gitlab.page(matches, pageValue(page), perPageValue(page));
        } catch (GitlabNotFoundException unsupportedEndpoint) {
            return legacyFind(jobApi, pattern, regex, page);
        }
    }

    private boolean supportsArtifactTree() {
        return serverInfoProvider.get().capabilities().contains(GitlabCapability.ARTIFACT_TREE);
    }

    private List<ArtifactFile> legacyList(
            String jobApi,
            String path,
            Boolean recursive,
            GitlabPageRequest page
    ) {
        return gitlab.listArtifactArchive(
                jobApi + "/artifacts",
                path,
                recursive,
                pageValue(page),
                perPageValue(page));
    }

    private List<ArtifactFile> legacyFind(
            String jobApi,
            String pattern,
            Boolean regex,
            GitlabPageRequest page
    ) {
        return gitlab.findArtifactArchiveFiles(
                jobApi + "/artifacts",
                pattern,
                regex,
                pageValue(page),
                perPageValue(page));
    }

    private Pattern compilePathPattern(String pattern, Boolean regex) {
        String effectivePattern = pattern == null || pattern.isBlank() ? "**" : pattern.strip();
        if (regex != null && regex) {
            return Pattern.compile(effectivePattern);
        }
        StringBuilder result = new StringBuilder("^");
        for (int i = 0; i < effectivePattern.length(); i++) {
            char character = effectivePattern.charAt(i);
            if (character == '*') {
                boolean doubleStar = i + 1 < effectivePattern.length()
                        && effectivePattern.charAt(i + 1) == '*';
                result.append(doubleStar ? ".*" : "[^/]*");
                if (doubleStar) {
                    i++;
                }
            } else if (character == '?') {
                result.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(character) >= 0) {
                result.append('\\').append(character);
            } else {
                result.append(character);
            }
        }
        return Pattern.compile(result.append('$').toString());
    }

    private Integer pageValue(GitlabPageRequest page) {
        return page == null ? null : page.page();
    }

    private Integer perPageValue(GitlabPageRequest page) {
        return page == null ? null : page.perPage();
    }
}
