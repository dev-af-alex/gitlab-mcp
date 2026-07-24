package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecretRedactor {

    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)(\\bPRIVATE-TOKEN\\b\\s*[:=]\\s*)([^\\s'\";]+)"),
            Pattern.compile(
                    "(?i)(\\b(?:password|passwd|secret|token|api[_-]?key|apikey|access[_-]?key|private[_-]?key|client[_-]?secret)\\b\\s*[:=]\\s*)([^\\s'\";]+)"),
            Pattern.compile(
                    "(?i)(\\b[A-Z0-9_]*(?:PASSWORD|PASSWD|SECRET|TOKEN|API_KEY|APIKEY|ACCESS_KEY|PRIVATE_KEY|CLIENT_SECRET)[A-Z0-9_]*\\b\\s*=\\s*)([^\\s'\";]+)"));

    public String redact(String text) {
        if (!StringUtils.hasText(text)) {
            return text == null ? "" : text;
        }
        String redacted = text;
        for (Pattern pattern : SECRET_PATTERNS) {
            Matcher matcher = pattern.matcher(redacted);
            redacted = matcher.replaceAll("$1[REDACTED]");
        }
        return redacted;
    }
}
