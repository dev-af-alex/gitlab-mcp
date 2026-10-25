package com.alexaf.gitlabmcp.application.discussion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alexaf.gitlabmcp.domain.CodeContext.Line;

final class UnifiedDiffReader {
    private static final Pattern HEADER =
            Pattern.compile("^@@ -(\\d{1,9})(?:,(\\d{1,9}))? \\+(\\d{1,9})(?:,(\\d{1,9}))? @@.*");

    private UnifiedDiffReader() {}

    static List<List<Line>> read(String diff) {
        if (diff == null || diff.isBlank()) {
            return List.of();
        }
        return readHunks(diff);
    }

    private static List<List<Line>> readHunks(String diff) {
        List<List<Line>> hunks = new ArrayList<>();
        Hunk hunk = null;
        for (String text : diff.lines().toList()) {
            Matcher header = HEADER.matcher(text);
            if (header.matches()) {
                if (hunk != null && !hunk.complete()) {
                    return List.of();
                }
                hunk = new Hunk(header);
                hunks.add(hunk.lines);
            } else if (hunk != null && !hunk.add(text)) {
                return List.of();
            }
        }
        return hunk != null && hunk.complete() ? hunks : List.of();
    }

    private static final class Hunk {
        private final List<Line> lines = new ArrayList<>();
        private int oldLine;
        private int newLine;
        private int oldRemaining;
        private int newRemaining;

        private Hunk(Matcher header) {
            oldLine = Integer.parseInt(header.group(1));
            newLine = Integer.parseInt(header.group(3));
            oldRemaining = count(header.group(2));
            newRemaining = count(header.group(4));
        }

        private static int count(String value) {
            return value == null ? 1 : Integer.parseInt(value);
        }

        private boolean complete() {
            return oldRemaining == 0 && newRemaining == 0;
        }

        private boolean add(String text) {
            if (text.equals("\\ No newline at end of file")) {
                return true;
            }
            if (text.isEmpty()) {
                return false;
            }
            String content = text.substring(1);
            switch (text.charAt(0)) {
                case ' ' -> {
                    lines.add(new Line(oldLine++, newLine++, "context", content, false));
                    oldRemaining--;
                    newRemaining--;
                }
                case '-' -> {
                    lines.add(new Line(oldLine++, null, "removed", content, false));
                    oldRemaining--;
                }
                case '+' -> {
                    lines.add(new Line(null, newLine++, "added", content, false));
                    newRemaining--;
                }
                default -> {
                    return false;
                }
            }
            return oldRemaining >= 0 && newRemaining >= 0;
        }
    }
}
