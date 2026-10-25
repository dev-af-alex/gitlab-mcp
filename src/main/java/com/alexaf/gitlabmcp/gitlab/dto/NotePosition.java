package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NotePosition(
        @JsonProperty("base_sha") String baseSha,
        @JsonProperty("start_sha") String startSha,
        @JsonProperty("head_sha") String headSha,
        @JsonProperty("position_type") String positionType,
        @JsonProperty("old_path") String oldPath,
        @JsonProperty("new_path") String newPath,
        @JsonProperty("old_line") Integer oldLine,
        @JsonProperty("new_line") Integer newLine,
        @JsonProperty("line_range") LineRange lineRange) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineRange(LinePosition start, LinePosition end) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinePosition(
            @JsonProperty("line_code") String lineCode,
            String type,
            @JsonProperty("old_line") Integer oldLine,
            @JsonProperty("new_line") Integer newLine) {}
}
