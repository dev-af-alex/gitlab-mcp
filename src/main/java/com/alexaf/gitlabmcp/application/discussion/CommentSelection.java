package com.alexaf.gitlabmcp.application.discussion;

import com.alexaf.gitlabmcp.domain.CodeContext.Line;
import com.alexaf.gitlabmcp.gitlab.dto.NotePosition;
import com.alexaf.gitlabmcp.gitlab.dto.NotePosition.LinePosition;

record CommentSelection(Endpoint start, Endpoint end) {
    static CommentSelection from(NotePosition position) {
        if (position.lineRange() != null) {
            return new CommentSelection(
                    endpoint(position.lineRange().start()),
                    endpoint(position.lineRange().end()));
        }
        Endpoint point = new Endpoint(position.oldLine(), position.newLine());
        return new CommentSelection(point, point);
    }

    boolean valid() {
        return start != null && end != null && start.valid() && end.valid() && ordered();
    }

    private boolean ordered() {
        if (start.newLine() != null && end.newLine() != null) {
            return start.newLine() <= end.newLine();
        }
        if (start.oldLine() != null && end.oldLine() != null) {
            return start.oldLine() <= end.oldLine();
        }
        return true; // Mixed sides are ordered by their actual positions in the diff.
    }

    private static Endpoint endpoint(LinePosition point) {
        if (point == null) {
            return null;
        }
        Endpoint coordinates = coordinates(point);
        return switch (point.type() == null ? "" : point.type()) {
            case "old" -> new Endpoint(coordinates.oldLine(), null);
            case "new" -> new Endpoint(null, coordinates.newLine());
            default -> null;
        };
    }

    private static Endpoint coordinates(LinePosition point) {
        if (point.oldLine() != null || point.newLine() != null || point.lineCode() == null) {
            return new Endpoint(point.oldLine(), point.newLine());
        }
        String[] parts = point.lineCode().split("_");
        return parts.length == 3 ? new Endpoint(number(parts[1]), number(parts[2])) : new Endpoint(null, null);
    }

    private static Integer number(String value) {
        try {
            int number = Integer.parseInt(value);
            return number > 0 ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    record Endpoint(Integer oldLine, Integer newLine) {
        boolean valid() {
            return (oldLine != null || newLine != null)
                    && (oldLine == null || oldLine > 0)
                    && (newLine == null || newLine > 0);
        }

        boolean matches(Line line) {
            return (oldLine == null || oldLine.equals(line.oldLine()))
                    && (newLine == null || newLine.equals(line.newLine()));
        }
    }
}
