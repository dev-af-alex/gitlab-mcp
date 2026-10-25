package com.alexaf.gitlabmcp.application.discussion;

import java.util.ArrayList;
import java.util.List;

import com.alexaf.gitlabmcp.domain.CodeContext;
import com.alexaf.gitlabmcp.domain.CodeContext.Line;
import com.alexaf.gitlabmcp.domain.CodeContext.Status;

final class ContextExcerpt {
    private static final int MAX_LINES = 200;
    private static final int MAX_CHARACTERS = 60_000;

    private ContextExcerpt() {}

    static CodeContext fromDiff(String diff, CommentSelection selection, int radius, Long versionId) {
        for (List<Line> hunk : UnifiedDiffReader.read(diff)) {
            int start = index(hunk, selection.start());
            int end = index(hunk, selection.end());
            if (start >= 0 && end >= start) {
                boolean missingContext = start < radius || end + radius >= hunk.size();
                CodeContext excerpt = slice(hunk, start, end, radius, "diff", versionId);
                if (missingContext && !excerpt.truncated()) {
                    return new CodeContext(
                            Status.PARTIAL,
                            "diff",
                            "Requested surrounding lines are outside the diff hunk",
                            versionId,
                            false,
                            excerpt.lines());
                }
                return excerpt;
            }
        }
        return null;
    }

    static CodeContext fromFile(String text, int start, int end, boolean newSide, int radius, Long versionId) {
        if (text.indexOf('\0') >= 0) {
            return unavailable("File contains binary data", versionId);
        }
        List<String> source = text.lines().toList();
        if (end > source.size()) {
            return unavailable("Comment line is outside the file at the pinned commit", versionId);
        }
        // Keep only the requested window; do not allocate a Line object for every line in the file.
        int from = Math.max(0, start - 1 - radius);
        int to = (int) Math.min(source.size(), (long) end + radius);
        List<Line> lines = new ArrayList<>();
        for (int i = from; i < to && lines.size() <= MAX_LINES; i++) {
            lines.add(new Line(newSide ? null : i + 1, newSide ? i + 1 : null, "source", source.get(i), false));
        }
        CodeContext excerpt = slice(lines, start - 1 - from, end - 1 - from, radius, "repository", versionId);
        boolean truncated = excerpt.truncated() || to - from > MAX_LINES;
        return new CodeContext(
                truncated ? Status.PARTIAL : Status.EXACT,
                "repository",
                truncated ? "Context output limit reached" : null,
                versionId,
                truncated,
                excerpt.lines());
    }

    static CodeContext unavailable(String reason, Long versionId) {
        return new CodeContext(Status.UNAVAILABLE, null, reason, versionId, false, List.of());
    }

    private static int index(List<Line> lines, CommentSelection.Endpoint point) {
        for (int i = 0; i < lines.size(); i++) {
            if (point.matches(lines.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static CodeContext slice(List<Line> lines, int start, int end, int radius, String source, Long versionId) {
        int from = Math.max(0, start - radius);
        int to = (int) Math.min(lines.size(), (long) end + radius + 1);
        List<Line> result = new ArrayList<>();
        int characters = 0;
        boolean truncated = false;
        for (int i = from; i < to; i++) {
            Line line = lines.get(i);
            int remaining = MAX_CHARACTERS - characters;
            if (result.size() == MAX_LINES || remaining == 0) {
                truncated = true;
                break;
            }
            String text = line.text();
            if (text.length() > remaining) {
                text = text.substring(0, remaining);
                truncated = true;
            }
            result.add(new Line(line.oldLine(), line.newLine(), line.kind(), text, i >= start && i <= end));
            characters += text.length();
        }
        return new CodeContext(
                truncated ? Status.PARTIAL : Status.EXACT,
                source,
                truncated ? "Context output limit reached" : null,
                versionId,
                truncated,
                List.copyOf(result));
    }
}
