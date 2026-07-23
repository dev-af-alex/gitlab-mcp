package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.util.List;

public record LogMatchResult(
        String pattern,
        boolean regex,
        int inspectedLines,
        int returnedMatches,
        boolean truncated,
        List<LogMatch> matches
) {

    public LogMatchResult compact() {
        return new LogMatchResult(
                pattern,
                regex,
                inspectedLines,
                returnedMatches,
                truncated,
                matches.stream()
                        .map(match -> new LogMatch(match.line(), match.text(), List.of(), List.of()))
                        .toList());
    }
}
