package com.alexaf.gitlabmcp.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FileChange(
        @JsonProperty("old_path") String oldPath,
        @JsonProperty("new_path") String newPath,
        @JsonProperty("a_mode") String aMode,
        @JsonProperty("b_mode") String bMode,
        @JsonProperty("new_file") Boolean newFile,
        @JsonProperty("renamed_file") Boolean renamedFile,
        @JsonProperty("deleted_file") Boolean deletedFile,
        String diff
) {
}
