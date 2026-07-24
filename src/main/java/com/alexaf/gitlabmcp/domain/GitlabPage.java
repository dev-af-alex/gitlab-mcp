package com.alexaf.gitlabmcp.domain;

import java.util.List;

public record GitlabPage<T>(List<T> items, String nextLink, int totalFetched, boolean truncated) {

    public GitlabPage {
        items = List.copyOf(items);
    }
}
