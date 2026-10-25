package com.alexaf.gitlabmcp.application.discussion;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alexaf.gitlabmcp.domain.CodeContext;
import com.alexaf.gitlabmcp.domain.CodeContext.Status;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabForbiddenException;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabNotFoundException;
import com.alexaf.gitlabmcp.gitlab.dto.Discussion;
import com.alexaf.gitlabmcp.gitlab.dto.FileChange;
import com.alexaf.gitlabmcp.gitlab.dto.MergeRequestDiffVersion;
import com.alexaf.gitlabmcp.gitlab.dto.Note;
import com.alexaf.gitlabmcp.gitlab.dto.NotePosition;
import com.alexaf.gitlabmcp.gitlab.dto.NotePosition.LinePosition;
import com.alexaf.gitlabmcp.gitlab.dto.NotePosition.LineRange;
import com.alexaf.gitlabmcp.port.GitlabGateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DiscussionContextServiceTest {
    private static final String BASE = "a".repeat(40);
    private static final String START = "b".repeat(40);
    private static final String HEAD = "c".repeat(40);
    private GitlabGateway gateway;
    private DiscussionContextService service;

    @BeforeEach
    void setUp() {
        gateway = mock(GitlabGateway.class);
        service = new DiscussionContextService(gateway);
    }

    @Test
    void returnsHistoricalAddedLineAndRepliesWithoutReadingCurrentDiff() {
        discussion(position(null, 2, null));
        // A new push has produced a different version with the same file and line numbers.
        when(gateway.listMergeRequestDiffVersions("p", "17", new GitlabPageRequest(1, 20)))
                .thenReturn(List.of(new MergeRequestDiffVersion(9L, BASE, START, "d".repeat(40), "collected", null)));
        when(gateway.listMergeRequestDiffVersions("p", "17", new GitlabPageRequest(2, 20)))
                .thenReturn(List.of(version(null)));
        when(gateway.getMergeRequestDiffVersion("p", "17", 8))
                .thenReturn(version("@@ -1,2 +1,3 @@\n before\n+historical code\n after\n"));

        var result = service.get("p", "17", "thread", 1);

        assertThat(result.anchorNoteId()).isEqualTo(1);
        assertThat(result.discussion().notes()).hasSize(2);
        assertThat(result.context().status()).isEqualTo(Status.EXACT);
        assertThat(result.context().versionId()).isEqualTo(8);
        assertThat(result.context().lines())
                .containsExactly(
                        new CodeContext.Line(1, 1, "context", "before", false),
                        new CodeContext.Line(null, 2, "added", "historical code", true),
                        new CodeContext.Line(2, 3, "context", "after", false));
        verify(gateway, never()).getMergeRequestChanges(anyString(), anyString());
        verify(gateway, never()).getRepositoryFile(anyString(), anyString(), anyString());
    }

    @Test
    void deletedLineFallsBackToOldPathAtBaseNotTargetHead() {
        discussion(position(2, null, null));
        when(gateway.getRepositoryFile("p", "old.java", BASE)).thenReturn("before\ndeleted code\nafter\n");

        CodeContext context = service.get("p", "17", "thread", 1).context();

        assertThat(context.status()).isEqualTo(Status.EXACT);
        assertThat(context.source()).isEqualTo("repository");
        assertThat(context.lines().get(1)).isEqualTo(new CodeContext.Line(2, null, "source", "deleted code", true));
        verify(gateway, never()).getRepositoryFile("p", "old.java", START);
    }

    @Test
    void renamedFileUsesNewPathAndPinnedHeadWhenDiffIsTruncated() {
        discussion(position(null, 2, null));
        matchingVersion("@@ -1,2 +1,3 @@\n before\n");
        when(gateway.getRepositoryFile("p", "new.java", HEAD)).thenReturn("before\nrenamed code\nafter\n");

        CodeContext context = service.get("p", "17", "thread", 1).context();

        assertThat(context.status()).isEqualTo(Status.EXACT);
        assertThat(context.source()).isEqualTo("repository");
        assertThat(context.lines().get(1).text()).isEqualTo("renamed code");
    }

    @Test
    void supportsMultilineRangeUsingGitlab15LineCodes() {
        LineRange range = new LineRange(
                new LinePosition("hash_0_2", "new", null, null), new LinePosition("hash_0_3", "new", null, null));
        discussion(position(null, 3, range));
        matchingVersion("@@ -1 +1,3 @@\n before\n+first\n+second\n");

        CodeContext context = service.get("p", "17", "thread", 0).context();

        assertThat(context.status()).isEqualTo(Status.EXACT);
        assertThat(context.lines()).extracting(CodeContext.Line::text).containsExactly("first", "second");
        assertThat(context.lines()).allMatch(CodeContext.Line::highlighted);
    }

    @Test
    void missingHistoricalCommitReturnsReasonWithoutSubstitutingCurrentCode() {
        discussion(position(null, 2, null));
        when(gateway.getRepositoryFile("p", "new.java", HEAD))
                .thenThrow(new GitlabNotFoundException(URI.create("https://gitlab.example/file")));

        CodeContext context = service.get("p", "17", "thread", null).context();

        assertThat(context.status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(context.reason()).contains("Matching diff version not found", "pinned commit");
        assertThat(context.lines()).isEmpty();
        verify(gateway, never()).getMergeRequestChanges(anyString(), anyString());
    }

    @Test
    void retainsPartialDiffWhenSurroundingFileCannotBeRead() {
        discussion(position(null, 2, null));
        matchingVersion("@@ -1 +1,2 @@\n before\n+added\n");
        when(gateway.getRepositoryFile("p", "new.java", HEAD))
                .thenThrow(new GitlabForbiddenException(URI.create("https://gitlab.example/file")));

        CodeContext context = service.get("p", "17", "thread", 5).context();

        assertThat(context.status()).isEqualTo(Status.PARTIAL);
        assertThat(context.reason()).contains("outside the diff hunk", "inaccessible");
        assertThat(context.lines())
                .anyMatch(line -> line.highlighted() && line.text().equals("added"));
    }

    @Test
    void checksAllThreeShasEvenWhenHeadIsUnchanged() {
        discussion(position(null, 2, null));
        when(gateway.listMergeRequestDiffVersions("p", "17", new GitlabPageRequest(1, 20)))
                .thenReturn(List.of(new MergeRequestDiffVersion(9L, BASE, "d".repeat(40), HEAD, "collected", null)));
        when(gateway.getRepositoryFile("p", "new.java", HEAD)).thenReturn("one\ntwo\n");

        assertThat(service.get("p", "17", "thread", 0).context().source()).isEqualTo("repository");
        verify(gateway, never()).getMergeRequestDiffVersion("p", "17", 9);
    }

    @Test
    void boundsVersionSearchEvenWhenServerKeepsReturningPages() {
        discussion(position(null, 2, null));
        when(gateway.listMergeRequestDiffVersions(eq("p"), eq("17"), any()))
                .thenReturn(List.of(new MergeRequestDiffVersion(9L, BASE, START, "d".repeat(40), "collected", null)));
        when(gateway.getRepositoryFile("p", "new.java", HEAD)).thenReturn("one\ntwo");

        assertThat(service.get("p", "17", "thread", 0).context().status()).isEqualTo(Status.EXACT);
        verify(gateway).listMergeRequestDiffVersions("p", "17", new GitlabPageRequest(100, 20));
        verify(gateway, never()).listMergeRequestDiffVersions("p", "17", new GitlabPageRequest(101, 20));
    }

    @Test
    void ordinaryDiscussionNeedsNoCodeRequests() {
        discussion(null);
        assertThat(service.get("p", "17", "thread", null).context().status()).isEqualTo(Status.NO_POSITION);
        verify(gateway).getMergeRequestDiscussion("p", "17", "thread");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void imagePositionIsExplicitlyUnsupported() {
        discussion(new NotePosition(BASE, START, HEAD, "image", "a.png", "a.png", null, null, null));
        assertThat(service.get("p", "17", "thread", null).context().status()).isEqualTo(Status.UNSUPPORTED);
        verify(gateway).getMergeRequestDiscussion("p", "17", "thread");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void rejectsInvalidRangeAndRadius() {
        discussion(position(
                null,
                3,
                new LineRange(new LinePosition(null, "new", null, 5), new LinePosition(null, "new", null, 3))));
        assertThat(service.get("p", "17", "thread", 0).context().status()).isEqualTo(Status.UNAVAILABLE);
        assertThatThrownBy(() -> service.get("p", "17", "thread", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.get("p", "17", "thread", 21)).isInstanceOf(IllegalArgumentException.class);
    }

    private void discussion(NotePosition position) {
        Note anchor = new Note(1L, "DiffNote", "Please fix", null, null, null, false, true, false, null, position);
        Note reply = new Note(2L, "DiscussionNote", "Working on it", null, null, null, false, null, null, null, null);
        when(gateway.getMergeRequestDiscussion("p", "17", "thread"))
                .thenReturn(new Discussion("thread", false, List.of(anchor, reply)));
    }

    private NotePosition position(Integer oldLine, Integer newLine, LineRange range) {
        return new NotePosition(BASE, START, HEAD, "text", "old.java", "new.java", oldLine, newLine, range);
    }

    private void matchingVersion(String diff) {
        when(gateway.listMergeRequestDiffVersions("p", "17", new GitlabPageRequest(1, 20)))
                .thenReturn(List.of(version(null)));
        when(gateway.getMergeRequestDiffVersion("p", "17", 8)).thenReturn(version(diff));
    }

    private MergeRequestDiffVersion version(String diff) {
        return new MergeRequestDiffVersion(
                8L,
                BASE,
                START,
                HEAD,
                "collected",
                List.of(new FileChange("old.java", "new.java", null, null, false, true, false, diff)));
    }
}
