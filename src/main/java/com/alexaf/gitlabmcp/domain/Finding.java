package com.alexaf.gitlabmcp.domain;

import java.util.List;

public record Finding(
        FindingCategory category,
        String toolchain,
        Confidence confidence,
        String summary,
        List<Evidence> evidence,
        List<String> recommendations
) {

    public Finding {
        evidence = List.copyOf(evidence);
        recommendations = List.copyOf(recommendations);
    }
}
