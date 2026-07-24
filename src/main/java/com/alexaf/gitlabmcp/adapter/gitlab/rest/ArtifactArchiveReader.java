package com.alexaf.gitlabmcp.adapter.gitlab.rest;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;

@Component
public class ArtifactArchiveReader {

    public List<ArtifactFile> list(Path archive, String directory, Boolean recursive) {
        String normalizedDirectory = normalizeDirectory(directory);
        boolean recursiveMode = recursive == null || recursive;
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(entry -> artifactFile(entry, normalizedDirectory, recursiveMode))
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(ArtifactFile::path))
                    .toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read GitLab artifacts archive", e);
        }
    }

    public List<ArtifactFile> find(Path archive, Pattern pattern) {
        return list(archive, null, true).stream()
                .filter(artifact -> "file".equals(artifact.type()))
                .filter(artifact -> pattern.matcher(artifact.path()).matches())
                .toList();
    }

    private Optional<ArtifactFile> artifactFile(ZipEntry entry, String directory, boolean recursive) {
        String entryName = trimLeadingSlash(entry.getName());
        if (!entryName.startsWith(directory)) {
            return Optional.empty();
        }
        String relative = entryName.substring(directory.length());
        if (relative.isBlank() || (!recursive && relative.contains("/"))) {
            return Optional.empty();
        }
        return Optional.of(new ArtifactFile(
                fileName(entryName), entryName, "file", entry.getSize() >= 0 ? entry.getSize() : null, null));
    }

    private String normalizeDirectory(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String normalized = trimLeadingSlash(path.strip());
        normalized = trimTrailingSlash(normalized);
        return normalized.isBlank() ? "" : normalized + "/";
    }

    private String fileName(String path) {
        int index = path.lastIndexOf('/');
        return index >= 0 ? path.substring(index + 1) : path;
    }

    private String trimLeadingSlash(String value) {
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
