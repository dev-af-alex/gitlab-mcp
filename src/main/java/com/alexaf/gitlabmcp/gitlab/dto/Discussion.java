package com.alexaf.gitlabmcp.gitlab.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Discussion(
        String id, @JsonProperty("individual_note") Boolean individualNote, List<Note> notes) {}
