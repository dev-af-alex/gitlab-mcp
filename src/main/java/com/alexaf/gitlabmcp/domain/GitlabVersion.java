package com.alexaf.gitlabmcp.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record GitlabVersion(int major, int minor, int patch, String edition, String raw)
        implements Comparable<GitlabVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+.]?([A-Za-z][A-Za-z0-9.-]*))?.*$");

    public static GitlabVersion parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GitLab version must be set");
        }
        String raw = value.trim();
        Matcher matcher = VERSION_PATTERN.matcher(raw);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported GitLab version format: " + value);
        }
        return new GitlabVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                matcher.group(4),
                raw);
    }

    public static GitlabVersion of(int major, int minor, int patch) {
        String raw = major + "." + minor + "." + patch;
        return new GitlabVersion(major, minor, patch, null, raw);
    }

    public boolean isAtLeast(GitlabVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(GitlabVersion other) {
        int majorComparison = Integer.compare(major, other.major);
        if (majorComparison != 0) {
            return majorComparison;
        }
        int minorComparison = Integer.compare(minor, other.minor);
        if (minorComparison != 0) {
            return minorComparison;
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return raw;
    }
}
