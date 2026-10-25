package com.alexaf.gitlabmcp.application.discussion;

import org.junit.jupiter.api.Test;

import com.alexaf.gitlabmcp.domain.CodeContext;
import com.alexaf.gitlabmcp.domain.CodeContext.Status;

import static org.assertj.core.api.Assertions.assertThat;

class ContextExcerptTest {
    @Test
    void mapsRemovedAndUnchangedLinesWithIndependentCounters() {
        String diff = "@@ -10,3 +20,2 @@ method\n context\n-deleted\n tail\n\\ No newline at end of file\n";
        var removed = ContextExcerpt.fromDiff(diff, selection(11, null), 1, 8L);
        assertThat(removed.status()).isEqualTo(Status.EXACT);
        assertThat(removed.lines())
                .containsExactly(
                        new CodeContext.Line(10, 20, "context", "context", false),
                        new CodeContext.Line(11, null, "removed", "deleted", true),
                        new CodeContext.Line(12, 21, "context", "tail", false));
        var unchanged = ContextExcerpt.fromDiff(diff, selection(12, 21), 0, 8L);
        assertThat(unchanged.lines())
                .singleElement()
                .satisfies(line -> assertThat(line.highlighted()).isTrue());
    }

    @Test
    void supportsNewAndDeletedFilesWithZeroLengthSide() {
        assertThat(ContextExcerpt.fromDiff("@@ -0,0 +1 @@\n+new file\n", selection(null, 1), 0, 1L)
                        .status())
                .isEqualTo(Status.EXACT);
        assertThat(ContextExcerpt.fromDiff("@@ -1 +0,0 @@\n-deleted file\n", selection(1, null), 0, 1L)
                        .status())
                .isEqualTo(Status.EXACT);
    }

    @Test
    void doesNotJoinUnrelatedHunksOrAcceptIncompleteDiff() {
        String diff = "@@ -1 +1 @@\n first\n@@ -100 +100 @@\n last\n";
        var context = ContextExcerpt.fromDiff(diff, selection(null, 1), 5, 1L);
        assertThat(context.lines()).extracting(CodeContext.Line::text).containsExactly("first");
        assertThat(context.status()).isEqualTo(Status.PARTIAL);
        assertThat(ContextExcerpt.fromDiff("@@ -1,2 +1,2 @@\n first\n", selection(null, 1), 0, 1L))
                .isNull();
        assertThat(ContextExcerpt.fromDiff("@@ -999999999999 +1 @@\n first\n", selection(null, 1), 0, 1L))
                .isNull();
    }

    @Test
    void mixedSideRangeHighlightsRemovalAndAddition() {
        var selection =
                new CommentSelection(new CommentSelection.Endpoint(2, null), new CommentSelection.Endpoint(null, 2));
        var result = ContextExcerpt.fromDiff("@@ -1,2 +1,2 @@\n before\n-old\n+new\n", selection, 0, 1L);
        assertThat(result.status()).isEqualTo(Status.EXACT);
        assertThat(result.lines()).extracting(CodeContext.Line::kind).containsExactly("removed", "added");
        assertThat(result.lines()).allMatch(CodeContext.Line::highlighted);
    }

    @Test
    void fileFallbackKeepsLineNumbersAndBoundsLargeRanges() {
        var excerpt = ContextExcerpt.fromFile("line\n".repeat(1000), 500, 501, true, 5, 1L);
        assertThat(excerpt.status()).isEqualTo(Status.EXACT);
        assertThat(excerpt.lines().getFirst().newLine()).isEqualTo(495);
        assertThat(excerpt.lines().getLast().newLine()).isEqualTo(506);
        assertThat(excerpt.lines().stream().filter(CodeContext.Line::highlighted))
                .extracting(CodeContext.Line::newLine)
                .containsExactly(500, 501);
        var large = ContextExcerpt.fromFile("line\n".repeat(1000), 1, 1000, true, 5, null);
        assertThat(large.status()).isEqualTo(Status.PARTIAL);
        assertThat(large.truncated()).isTrue();
        assertThat(large.lines()).hasSize(200);
    }

    @Test
    void boundsLongLinesAndRejectsBinaryOrOutOfBoundsPositions() {
        var longLine = ContextExcerpt.fromFile("x".repeat(100_000), 1, 1, true, 0, null);
        assertThat(longLine.truncated()).isTrue();
        assertThat(longLine.lines().getFirst().text()).hasSize(60_000);
        assertThat(ContextExcerpt.fromFile("binary\0", 1, 1, true, 0, null).status())
                .isEqualTo(Status.UNAVAILABLE);
        assertThat(ContextExcerpt.fromFile("one\n", 2, 2, true, 0, null).status())
                .isEqualTo(Status.UNAVAILABLE);
    }

    private CommentSelection selection(Integer oldLine, Integer newLine) {
        var point = new CommentSelection.Endpoint(oldLine, newLine);
        return new CommentSelection(point, point);
    }
}
