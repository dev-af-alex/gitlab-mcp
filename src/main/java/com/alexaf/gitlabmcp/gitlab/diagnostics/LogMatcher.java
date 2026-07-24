package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LogMatcher {

    private static final int DEFAULT_MAX_MATCHES = 20;

    private static List<String> context(String[] lines, int fromInclusive, int toExclusive) {
        List<String> result = new ArrayList<>();
        for (int i = fromInclusive; i < toExclusive; i++) {
            String line = lines[i].strip();
            if (StringUtils.hasText(line) && !line.toLowerCase(Locale.ROOT).startsWith("[truncated to ")) {
                result.add(line);
            }
        }
        return result;
    }

    public LogMatchResult findMatches( // NOPMD - existing complexity baseline
            String text,
            String pattern,
            Boolean regex,
            Integer contextBefore,
            Integer contextAfter,
            Integer maxMatches) {
        String effectivePattern = StringUtils.hasText(pattern) ? pattern.strip() : "ERROR";
        boolean regexMode = regex != null && regex;
        Pattern compiled = regexMode
                ? Pattern.compile(effectivePattern, Pattern.CASE_INSENSITIVE)
                : Pattern.compile(Pattern.quote(effectivePattern), Pattern.CASE_INSENSITIVE);
        int before = Math.max(0, contextBefore == null ? 3 : contextBefore);
        int after = Math.max(0, contextAfter == null ? 5 : contextAfter);
        int limit = maxMatches == null || maxMatches <= 0 ? DEFAULT_MAX_MATCHES : maxMatches;
        String[] lines = StringUtils.hasText(text) ? text.split("\\R") : new String[0];

        List<LogMatch> matches = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (compiled.matcher(lines[i]).find()) {
                matches.add(new LogMatch(
                        i + 1,
                        lines[i].strip(),
                        context(lines, Math.max(0, i - before), i),
                        context(lines, i + 1, Math.min(lines.length, i + 1 + after))));
                if (matches.size() == limit) {
                    break;
                }
            }
        }
        return new LogMatchResult(
                effectivePattern, regexMode, lines.length, matches.size(), matches.size() == limit, matches);
    }

    public LogMatchResult importantMatches(String trace) {
        return findMatches(
                trace,
                "Failures:|Errors:|expected:|BUILD FAILURE|COMPILATION ERROR|Failed to execute goal|Caused by:",
                true,
                4,
                8,
                12);
    }
}
