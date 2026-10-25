package com.alexaf.gitlabmcp.domain;

import java.util.List;

public record CodeContext(
        Status status, String source, String reason, Long versionId, boolean truncated, List<Line> lines) {
    public enum Status {
        EXACT,
        PARTIAL,
        UNAVAILABLE,
        NO_POSITION,
        UNSUPPORTED
    }

    public record Line(Integer oldLine, Integer newLine, String kind, String text, boolean highlighted) {}
}
