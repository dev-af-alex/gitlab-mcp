package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

public record GitlabHttpResponse(String body, HttpHeaders headers) {

    private static final Pattern NEXT_LINK =
            Pattern.compile("<([^>]+)>\\s*;\\s*rel\\s*=\\s*\"?next\"?", Pattern.CASE_INSENSITIVE);

    public GitlabHttpResponse {
        body = body == null ? "" : body;
        headers = HttpHeaders.readOnlyHttpHeaders(headers);
    }

    public URI nextLink() {
        String link = headers.getFirst(HttpHeaders.LINK);
        if (!StringUtils.hasText(link)) {
            return null;
        }
        Matcher matcher = NEXT_LINK.matcher(link);
        return matcher.find() ? URI.create(matcher.group(1)) : null;
    }
}
