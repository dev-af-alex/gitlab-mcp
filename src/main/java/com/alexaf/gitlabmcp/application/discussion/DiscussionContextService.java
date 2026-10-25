package com.alexaf.gitlabmcp.application.discussion;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.alexaf.gitlabmcp.domain.CodeContext;
import com.alexaf.gitlabmcp.domain.CodeContext.Status;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabDecodeException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabDownloadLimitException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabForbiddenException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabNotFoundException;
import com.alexaf.gitlabmcp.gitlab.dto.Discussion;
import com.alexaf.gitlabmcp.gitlab.dto.FileChange;
import com.alexaf.gitlabmcp.gitlab.dto.MergeRequestDiffVersion;
import com.alexaf.gitlabmcp.gitlab.dto.Note;
import com.alexaf.gitlabmcp.gitlab.dto.NotePosition;
import com.alexaf.gitlabmcp.port.GitlabGateway;

@Service
public class DiscussionContextService {
    private static final int MAX_VERSIONS = 100;
    private final GitlabGateway gitlab;

    public DiscussionContextService(GitlabGateway gitlab) {
        this.gitlab = gitlab;
    }

    public Result get(String projectId, String mergeRequestIid, String discussionId, Integer contextLines) {
        int radius = contextLines == null ? 5 : contextLines;
        if (radius < 0 || radius > 20) {
            throw new IllegalArgumentException("contextLines must be between 0 and 20");
        }
        Discussion discussion = gitlab.getMergeRequestDiscussion(projectId, mergeRequestIid, discussionId);
        Note anchor = discussion.notes() == null
                ? null
                : discussion.notes().stream()
                        .filter(note -> note.position() != null)
                        .findFirst()
                        .orElse(null);
        if (anchor == null) {
            return new Result(
                    discussion,
                    null,
                    new CodeContext(
                            Status.NO_POSITION, null, "Discussion has no code position", null, false, List.of()));
        }
        return new Result(discussion, anchor.id(), context(projectId, mergeRequestIid, anchor.position(), radius));
    }

    private CodeContext context(String projectId, String iid, NotePosition position, int radius) {
        if (!"text".equals(position.positionType())) {
            return new CodeContext(
                    Status.UNSUPPORTED, null, "Only text positions have line context", null, false, List.of());
        }
        CommentSelection selection = CommentSelection.from(position);
        if (!selection.valid() || !validRefs(position)) {
            return ContextExcerpt.unavailable(
                    "Comment has incomplete or invalid line coordinates or commit SHAs", null);
        }
        CodeContext diff = diffContext(projectId, iid, position, selection, radius);
        if (diff.status() == Status.EXACT || diff.truncated()) {
            return diff;
        }
        CodeContext file = fileContext(projectId, position, selection, radius, diff.versionId());
        if (file.status() != Status.UNAVAILABLE) {
            return file;
        }
        String reason = diff.reason() + "; " + file.reason();
        return new CodeContext(diff.status(), diff.source(), reason, diff.versionId(), diff.truncated(), diff.lines());
    }

    private CodeContext diffContext(
            String projectId, String iid, NotePosition position, CommentSelection selection, int radius) {
        try {
            MergeRequestDiffVersion version = findVersion(projectId, iid, position);
            if (version == null) {
                return ContextExcerpt.unavailable(
                        "Matching diff version not found within the 100-version search limit", null);
            }
            MergeRequestDiffVersion detail = gitlab.getMergeRequestDiffVersion(projectId, iid, version.id());
            if (!matches(detail, position)) {
                return ContextExcerpt.unavailable("Diff version SHAs do not match the comment", version.id());
            }
            return excerpt(detail, position, selection, radius);
        } catch (GitlabNotFoundException | GitlabForbiddenException e) {
            return ContextExcerpt.unavailable("Historical diff is missing or inaccessible", null);
        } catch (GitlabDownloadLimitException e) {
            return ContextExcerpt.unavailable("Historical diff exceeds the download limit", null);
        }
    }

    private CodeContext excerpt(
            MergeRequestDiffVersion version, NotePosition position, CommentSelection selection, int radius) {
        List<FileChange> files = version.diffs() == null ? List.of() : version.diffs();
        for (FileChange file : files) {
            if (Objects.equals(position.oldPath(), file.oldPath())
                    && Objects.equals(position.newPath(), file.newPath())) {
                CodeContext excerpt = ContextExcerpt.fromDiff(file.diff(), selection, radius, version.id());
                if (excerpt != null) {
                    return excerpt;
                }
            }
        }
        return ContextExcerpt.unavailable(
                "Matching file or complete comment range is absent from the stored diff", version.id());
    }

    private MergeRequestDiffVersion findVersion(String projectId, String iid, NotePosition position) {
        int inspected = 0;
        for (int page = 1; page <= MAX_VERSIONS && inspected < MAX_VERSIONS; page++) {
            List<MergeRequestDiffVersion> versions =
                    gitlab.listMergeRequestDiffVersions(projectId, iid, new GitlabPageRequest(page, 20));
            if (versions.isEmpty()) {
                break;
            }
            for (MergeRequestDiffVersion version : versions) {
                if (inspected++ >= MAX_VERSIONS) {
                    return null;
                }
                if (version.id() != null && matches(version, position)) {
                    return version;
                }
            }
        }
        return null;
    }

    private boolean matches(MergeRequestDiffVersion version, NotePosition position) {
        return Objects.equals(version.baseSha(), position.baseSha())
                && Objects.equals(version.startSha(), position.startSha())
                && Objects.equals(version.headSha(), position.headSha());
    }

    private CodeContext fileContext(
            String projectId, NotePosition position, CommentSelection selection, int radius, Long versionId) {
        boolean newSide = selection.start().newLine() != null && selection.end().newLine() != null;
        Integer start =
                newSide ? selection.start().newLine() : selection.start().oldLine();
        Integer end = newSide ? selection.end().newLine() : selection.end().oldLine();
        if (start == null || end == null) {
            return ContextExcerpt.unavailable("A mixed-side range requires the historical diff", versionId);
        }
        String path = newSide ? position.newPath() : position.oldPath();
        if (path == null || path.isBlank()) {
            return ContextExcerpt.unavailable("Comment has no file path for the selected side", versionId);
        }
        String sha = newSide ? position.headSha() : position.baseSha();
        return readFile(projectId, path, sha, start, end, newSide, radius, versionId);
    }

    private CodeContext readFile(
            String projectId,
            String path,
            String sha,
            int start,
            int end,
            boolean newSide,
            int radius,
            Long versionId) {
        try {
            String text = gitlab.getRepositoryFile(projectId, path, sha);
            return ContextExcerpt.fromFile(text, start, end, newSide, radius, versionId);
        } catch (GitlabNotFoundException | GitlabForbiddenException e) {
            return ContextExcerpt.unavailable("File at the pinned commit is missing or inaccessible", versionId);
        } catch (GitlabDownloadLimitException e) {
            return ContextExcerpt.unavailable("File at the pinned commit exceeds the download limit", versionId);
        } catch (GitlabDecodeException e) {
            return ContextExcerpt.unavailable("File at the pinned commit is not UTF-8 text", versionId);
        }
    }

    private boolean validRefs(NotePosition position) {
        return validSha(position.baseSha()) && validSha(position.startSha()) && validSha(position.headSha());
    }

    private boolean validSha(String sha) {
        return sha != null && sha.matches("[0-9a-fA-F]{40}");
    }

    public record Result(Discussion discussion, Long anchorNoteId, CodeContext context) {}
}
