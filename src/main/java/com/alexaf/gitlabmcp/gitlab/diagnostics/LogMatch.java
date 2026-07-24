package com.alexaf.gitlabmcp.gitlab.diagnostics;

import java.util.List;

public record LogMatch(int line, String text, List<String> before, List<String> after) {}
