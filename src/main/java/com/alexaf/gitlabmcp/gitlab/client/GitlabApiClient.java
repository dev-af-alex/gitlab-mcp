package com.alexaf.gitlabmcp.gitlab.client;

import com.alexaf.gitlabmcp.adapter.gitlab.rest.ArtifactArchiveReader;
import com.alexaf.gitlabmcp.adapter.gitlab.rest.GitlabHttpTransport;
import com.alexaf.gitlabmcp.adapter.gitlab.rest.GitlabQueryParameter;
import com.alexaf.gitlabmcp.adapter.gitlab.rest.SecretRedactor;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabDecodeException;
import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class GitlabApiClient {

    private static final String TRUNCATED_SUFFIX = "\n[truncated to %d bytes]";
    private static final String TAIL_TRUNCATED_PREFIX = "[truncated to last %d bytes]\n";
    private static final int DEFAULT_TEXT_BYTES = 60_000;

    private final GitlabProperties properties;
    private final ObjectMapper objectMapper;
    private final GitlabHttpTransport transport;
    private final ArtifactArchiveReader artifactArchiveReader;
    private final SecretRedactor secretRedactor;
    private final List<String> allowedProjects;

    @Autowired
    public GitlabApiClient(
            GitlabProperties properties,
            ObjectMapper objectMapper,
            GitlabHttpTransport transport,
            ArtifactArchiveReader artifactArchiveReader,
            SecretRedactor secretRedactor
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transport = transport;
        this.artifactArchiveReader = artifactArchiveReader;
        this.secretRedactor = secretRedactor;
        this.allowedProjects = normalizeAllowedProjects(properties.allowedProjects());
    }

    public GitlabApiClient(GitlabProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this(properties, objectMapper,
                new GitlabHttpTransport(properties, restClientBuilder),
                new ArtifactArchiveReader(),
                new SecretRedactor());
    }

    private static long numericGitlabId(String value, String name, List<Pattern> patterns) {
        String trimmed = requireText(value, name);
        trimmed = stripWrappingQuotes(trimmed);

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(trimmed);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        }

        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        if (!trimmed.matches("\\d+")) {
            throw new IllegalArgumentException(name + " must be a numeric id or GitLab URL: " + value);
        }
        return Long.parseLong(trimmed);
    }

    private static int effectiveMaxBytes(Integer maxBytes) {
        return maxBytes == null || maxBytes <= 0 ? DEFAULT_TEXT_BYTES : maxBytes;
    }

    private static Pattern compilePathPattern(String pattern, Boolean regex) {
        String effectivePattern = StringUtils.hasText(pattern) ? pattern.strip() : "**";
        if (regex != null && regex) {
            return Pattern.compile(effectivePattern);
        }
        return Pattern.compile(globToRegex(effectivePattern));
    }

    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                boolean doubleStar = i + 1 < glob.length() && glob.charAt(i + 1) == '*';
                if (doubleStar) {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return regex.append('$').toString();
    }

    private static void deleteTempFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
        }
    }

    private static String decodeUtf8Prefix(byte[] bytes, int maxBytes) {
        return decodeUtf8Slice(bytes, 0, Math.min(bytes.length, maxBytes));
    }

    private static String decodeUtf8Slice(byte[] bytes, int offset, int length) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.IGNORE)
                .onUnmappableCharacter(CodingErrorAction.IGNORE);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes, offset, length)).toString();
        } catch (Exception e) {
            return new String(bytes, offset, length, StandardCharsets.UTF_8);
        }
    }

    private static String encodeProjectId(String projectId) {
        String trimmed = requireText(projectId, "projectId");
        if (trimmed.matches("\\d+")) {
            return trimmed;
        }
        return UriUtils.encodePathSegment(trimmed, StandardCharsets.UTF_8);
    }

    private static List<String> normalizeAllowedProjects(List<String> projects) {
        if (projects == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String project : projects) {
            if (StringUtils.hasText(project)) {
                normalized.add(normalizeProject(project));
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalizeProject(String project) {
        return normalizeProjectId(project)
                .toLowerCase(Locale.ROOT);
    }

    private static String normalizeProjectId(String projectId) {
        String value = requireText(projectId, "projectId");
        value = stripWrappingQuotes(value);

        Matcher projectNumber = Pattern.compile("(?i)^project\\s+#?(\\d+)$").matcher(value);
        if (projectNumber.matches()) {
            return projectNumber.group(1);
        }

        if (value.matches("\\d+")) {
            return value;
        }

        value = extractProjectFromGitUrl(value);
        value = extractProjectFromWebUrl(value);

        int bangIndex = value.indexOf('!');
        if (bangIndex > 0) {
            value = value.substring(0, bangIndex);
        }

        value = value.replaceAll("\\s*/\\s*", "/");
        value = decode(value);
        value = trimLeadingSlash(value);
        value = trimTrailingSlash(value);

        if (value.endsWith(".git")) {
            value = value.substring(0, value.length() - ".git".length());
        }

        return requireText(value, "projectId");
    }

    private static String extractProjectFromGitUrl(String value) {
        Matcher sshMatcher = Pattern.compile("^[^@\\s]+@[^:\\s]+:(.+)$").matcher(value);
        if (sshMatcher.matches()) {
            return sshMatcher.group(1);
        }
        return value;
    }

    private static String extractProjectFromWebUrl(String value) {
        if (!value.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            return value;
        }
        try {
            URI uri = new URI(value);
            String path = requireText(uri.getRawPath(), "projectId");
            path = stripApiPrefix(path);

            int markerIndex = path.indexOf("/-/");
            if (markerIndex >= 0) {
                path = path.substring(0, markerIndex);
            }

            List<String> segments = new ArrayList<>(Arrays.asList(path.split("/")));
            segments.removeIf(String::isBlank);
            int projectsIndex = segments.indexOf("projects");
            if (projectsIndex >= 0 && projectsIndex < segments.size() - 1) {
                return segments.get(projectsIndex + 1);
            }
            return String.join("/", segments);
        } catch (URISyntaxException e) {
            return value;
        }
    }

    private static String stripApiPrefix(String path) {
        Set<String> prefixes = Set.of("/api/v4");
        for (String prefix : prefixes) {
            if (path.startsWith(prefix + "/")) {
                return path.substring(prefix.length());
            }
        }
        return path;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value
                .replace("%2F", "%2f"), StandardCharsets.UTF_8);
    }

    private static String stripWrappingQuotes(String value) {
        String result = value.trim();
        if (result.length() >= 2) {
            char first = result.charAt(0);
            char last = result.charAt(result.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return result.substring(1, result.length() - 1).trim();
            }
        }
        return result;
    }

    private static String trimLeadingSlash(String value) {
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private static String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " must be set");
        }
        return value.trim();
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public String get(String path, QueryParam... queryParams) {
        return prettyJson(getRawText(path, queryParams));
    }

    public String getRawText(String path, QueryParam... queryParams) {
        return transport.getText(path, queryParameters(queryParams));
    }

    public String getLimitedText(String path, Integer maxBytes, QueryParam... queryParams) {
        Path file = downloadToTempFile(path, queryParams);
        try {
            return readPrefixText(file, maxBytes);
        } finally {
            deleteTempFile(file);
        }
    }

    public String getTailText(String path, Integer maxBytes, QueryParam... queryParams) {
        Path file = downloadToTempFile(path, queryParams);
        try {
            return readTailText(file, maxBytes);
        } finally {
            deleteTempFile(file);
        }
    }

    public List<ArtifactFile> listArtifactArchive(String archivePath, String path, Boolean recursive, Integer page, Integer perPage) {
        Path file = downloadToTempFile(archivePath);
        try {
            return page(artifactArchiveReader.list(file, path, recursive), page, perPage);
        } finally {
            deleteTempFile(file);
        }
    }

    public List<ArtifactFile> findArtifactArchiveFiles(String archivePath, String pattern, Boolean regex, Integer page, Integer perPage) {
        Path file = downloadToTempFile(archivePath);
        try {
            Pattern compiled = compilePathPattern(pattern, regex);
            List<ArtifactFile> matches = artifactArchiveReader.find(file, compiled);
            return page(matches, page, perPage);
        } finally {
            deleteTempFile(file);
        }
    }

    public <T> T getObject(String path, Class<T> type, QueryParam... queryParams) {
        return readValue(get(path, queryParams), type);
    }

    public <T> List<T> getList(String path, Class<T> itemType, QueryParam... queryParams) {
        JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, itemType);
        return readValue(get(path, queryParams), type);
    }

    public String json(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize GitLab response", e);
        }
    }

    public String projectPath(String projectId) {
        String normalized = normalizeProjectId(projectId);
        requireProjectAllowed(normalized, projectId);
        return encodeProjectId(normalized);
    }

    public long mergeRequestIid(String value) {
        String trimmed = requireText(value, "mergeRequestIid");

        Matcher urlMatcher = Pattern.compile("/merge_requests/(\\d+)").matcher(trimmed);
        if (urlMatcher.find()) {
            return Long.parseLong(urlMatcher.group(1));
        }

        int bangIndex = trimmed.lastIndexOf('!');
        if (bangIndex >= 0 && bangIndex < trimmed.length() - 1) {
            trimmed = trimmed.substring(bangIndex + 1);
        }

        if (trimmed.startsWith("!")) {
            trimmed = trimmed.substring(1);
        }
        if (!trimmed.matches("\\d+")) {
            throw new IllegalArgumentException("mergeRequestIid must be a numeric IID, !IID, or GitLab merge request URL: " + value);
        }
        return Long.parseLong(trimmed);
    }

    public long pipelineId(String value) {
        return numericGitlabId(value, "pipelineId", List.of(
                Pattern.compile("/pipelines/(\\d+)"),
                Pattern.compile("(?i)^pipeline\\s+#?(\\d+)$")
        ));
    }

    public long jobId(String value) {
        return numericGitlabId(value, "jobId", List.of(
                Pattern.compile("/-/jobs/(\\d+)"),
                Pattern.compile("/jobs/(\\d+)"),
                Pattern.compile("(?i)^job\\s+#?(\\d+)$")
        ));
    }

    public String mergeRequestState(String state) {
        if (!StringUtils.hasText(state)) {
            return "opened";
        }
        String normalized = state.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "open", "opened" -> "opened";
            case "close", "closed" -> "closed";
            case "lock", "locked" -> "locked";
            case "merge", "merged" -> "merged";
            case "all", "any", "*" -> "all";
            default -> throw new IllegalArgumentException("Unsupported merge request state: " + state
                    + ". Supported values: opened, closed, locked, merged, all.");
        };
    }

    public int page(Integer page) {
        return Optional.ofNullable(page).filter(value -> value > 0).orElse(1);
    }

    public int perPage(Integer perPage) {
        int requested = Optional.ofNullable(perPage)
                .filter(value -> value > 0)
                .orElse(Math.max(1, properties.defaultPerPage()));
        int max = Math.max(1, properties.maxPerPage());
        return Math.min(requested, max);
    }

    public <T> List<T> page(List<T> values, Integer page, Integer perPage) {
        int effectivePage = page(page);
        int effectivePerPage = perPage(perPage);
        int from = Math.min(values.size(), (effectivePage - 1) * effectivePerPage);
        int to = Math.min(values.size(), from + effectivePerPage);
        return values.subList(from, to);
    }

    public QueryParam param(String name, Object value) {
        return new QueryParam(name, value);
    }

    public String redactSecrets(String text) {
        return secretRedactor.redact(text);
    }

    public String limitText(String text, Integer maxBytes) {
        if (text == null) {
            return "";
        }
        if (maxBytes == null || maxBytes <= 0) {
            return text;
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        return decodeUtf8Prefix(bytes, maxBytes).stripTrailing() + TRUNCATED_SUFFIX.formatted(maxBytes);
    }

    public String tailText(String text, Integer maxBytes) {
        if (text == null) {
            return "";
        }
        if (maxBytes == null || maxBytes <= 0) {
            return text;
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        return TAIL_TRUNCATED_PREFIX.formatted(maxBytes)
                + decodeUtf8Slice(bytes, Math.max(0, bytes.length - maxBytes), maxBytes).stripLeading();
    }

    private String prettyJson(String response) {
        if (!StringUtils.hasText(response)) {
            return "";
        }
        try {
            JsonNode json = objectMapper.readTree(response);
            return objectMapper.writeValueAsString(json);
        } catch (Exception ignored) {
            return response;
        }
    }

    private Path downloadToTempFile(String path, QueryParam... queryParams) {
        return transport.download(path, queryParameters(queryParams));
    }

    private String readPrefixText(Path file, Integer maxBytes) {
        try {
            long size = Files.size(file);
            int effectiveMaxBytes = effectiveMaxBytes(maxBytes);
            if (size <= effectiveMaxBytes) {
                return redactSecrets(Files.readString(file));
            }
            byte[] bytes = readBytes(file, 0, effectiveMaxBytes);
            return redactSecrets(decodeUtf8Prefix(bytes, effectiveMaxBytes).stripTrailing())
                    + TRUNCATED_SUFFIX.formatted(effectiveMaxBytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read GitLab response", e);
        }
    }

    private String readTailText(Path file, Integer maxBytes) {
        try {
            long size = Files.size(file);
            int effectiveMaxBytes = effectiveMaxBytes(maxBytes);
            if (size <= effectiveMaxBytes) {
                return redactSecrets(Files.readString(file));
            }
            long start = Math.max(0, size - effectiveMaxBytes);
            byte[] bytes = readBytes(file, start, effectiveMaxBytes);
            return TAIL_TRUNCATED_PREFIX.formatted(effectiveMaxBytes)
                    + redactSecrets(decodeUtf8Slice(bytes, 0, bytes.length).stripLeading());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read GitLab response", e);
        }
    }

    private byte[] readBytes(Path file, long start, int maxBytes) throws Exception {
        int length = (int) Math.min(Files.size(file) - start, maxBytes);
        byte[] bytes = new byte[length];
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            channel.position(start);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                // keep reading until the requested slice is filled or EOF is reached
            }
        }
        return bytes;
    }

    private List<GitlabQueryParameter> queryParameters(QueryParam... queryParams) {
        return Arrays.stream(queryParams)
                .map(param -> new GitlabQueryParameter(param.name(), param.value()))
                .toList();
    }

    private <T> T readValue(String response, Class<T> type) {
        try {
            return objectMapper.readValue(response, type);
        } catch (Exception e) {
            throw new GitlabDecodeException(" as " + type.getSimpleName(), e);
        }
    }

    private <T> T readValue(String response, JavaType type) {
        try {
            return objectMapper.readValue(response, type);
        } catch (Exception e) {
            throw new GitlabDecodeException("", e);
        }
    }

    private void requireProjectAllowed(String normalizedProjectId, String originalProjectId) {
        if (allowedProjects.isEmpty()) {
            return;
        }
        if (!allowedProjects.contains(normalizedProjectId)) {
            throw new IllegalArgumentException("Project is not allowed by GITLAB_ALLOWED_PROJECTS: " + originalProjectId);
        }
    }

    public record QueryParam(String name, Object value) {
    }
}
