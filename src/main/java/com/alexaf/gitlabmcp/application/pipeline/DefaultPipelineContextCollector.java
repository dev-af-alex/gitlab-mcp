package com.alexaf.gitlabmcp.application.pipeline;

import com.alexaf.gitlabmcp.domain.GitlabPage;
import com.alexaf.gitlabmcp.domain.GitlabPageRequest;
import com.alexaf.gitlabmcp.domain.PipelineCollectionOptions;
import com.alexaf.gitlabmcp.domain.PipelineContext;
import com.alexaf.gitlabmcp.domain.PipelineEdge;
import com.alexaf.gitlabmcp.domain.PipelineGraph;
import com.alexaf.gitlabmcp.domain.PipelineNode;
import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
import com.alexaf.gitlabmcp.gitlab.client.error.GitlabNotFoundException;
import com.alexaf.gitlabmcp.gitlab.dto.ArtifactFile;
import com.alexaf.gitlabmcp.gitlab.dto.Job;
import com.alexaf.gitlabmcp.gitlab.dto.Pipeline;
import com.alexaf.gitlabmcp.gitlab.dto.PipelineBridge;
import com.alexaf.gitlabmcp.port.GitlabGateway;
import com.alexaf.gitlabmcp.port.PipelineContextCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class DefaultPipelineContextCollector implements PipelineContextCollector {

    private final GitlabGateway gitlab;
    private final int maxJobs;
    private final int maxPipelines;
    private final int maxPipelineDepth;

    @Autowired
    public DefaultPipelineContextCollector(GitlabGateway gitlab, GitlabProperties properties) {
        this(gitlab, properties.maxJobs(), properties.maxPipelines(), properties.maxPipelineDepth());
    }

    public DefaultPipelineContextCollector(GitlabGateway gitlab, int maxJobs) {
        this(gitlab, maxJobs, 20, 3);
    }

    public DefaultPipelineContextCollector(
            GitlabGateway gitlab,
            int maxJobs,
            int maxPipelines,
            int maxPipelineDepth
    ) {
        this.gitlab = gitlab;
        this.maxJobs = Math.max(1, maxJobs);
        this.maxPipelines = Math.max(1, maxPipelines);
        this.maxPipelineDepth = Math.max(0, maxPipelineDepth);
    }

    @Override
    public PipelineContext collect(
            String projectId,
            String pipelineId,
            String mergeRequestIid,
            PipelineCollectionOptions options
    ) {
        Pipeline pipeline = resolvePipeline(projectId, pipelineId, mergeRequestIid);
        String rootJobsPipelineId = StringUtils.hasText(pipelineId)
                ? pipelineId
                : String.valueOf(pipeline.id());
        PipelineGraph graph = collectGraph(projectId, pipeline);
        List<Job> jobs = new ArrayList<>();
        Map<Long, String> jobProjects = new LinkedHashMap<>();
        boolean jobsTruncated = false;
        int totalJobsFetched = 0;
        for (PipelineNode node : graph.nodes()) {
            int remainingJobs = maxJobs - totalJobsFetched;
            if (remainingJobs <= 0) {
                jobsTruncated = true;
                break;
            }
            String jobsPipelineId = node.depth() == 0
                    ? rootJobsPipelineId
                    : String.valueOf(node.pipeline().id());
            GitlabPage<Job> page = gitlab.getPipelineJobs(
                    node.projectId(),
                    jobsPipelineId,
                    false,
                    remainingJobs);
            jobs.addAll(page.items());
            page.items().forEach(job -> jobProjects.put(job.id(), node.projectId()));
            totalJobsFetched += page.totalFetched();
            jobsTruncated |= page.truncated();
        }
        var testReport = gitlab.getPipelineTestReport(
                projectId,
                String.valueOf(pipeline.id()));
        Map<Long, String> traces = new LinkedHashMap<>();
        Map<Long, List<ArtifactFile>> artifacts = new LinkedHashMap<>();
        Map<String, String> junitReports = new LinkedHashMap<>();
        for (Job job : jobs) {
            if (!"failed".equals(job.status())) {
                continue;
            }
            String jobProjectId = jobProjects.getOrDefault(job.id(), projectId);
            collectTrace(jobProjectId, job, options, traces);
            collectArtifacts(jobProjectId, job, options, artifacts, junitReports);
        }
        return new PipelineContext(
                pipeline,
                jobs,
                traces,
                junitReports,
                artifacts,
                testReport.orElse(null),
                jobsTruncated,
                totalJobsFetched,
                graph,
                detectBuildSignals(jobs, traces, artifacts, junitReports));
    }

    private Set<String> detectBuildSignals(
            List<Job> jobs,
            Map<Long, String> traces,
            Map<Long, List<ArtifactFile>> artifacts,
            Map<String, String> junitReports
    ) {
        StringBuilder source = new StringBuilder();
        jobs.forEach(job -> source.append(' ')
                .append(job.name())
                .append(' ')
                .append(job.stage()));
        traces.values().forEach(trace -> source.append(' ').append(trace));
        artifacts.values().stream()
                .flatMap(List::stream)
                .map(ArtifactFile::path)
                .forEach(path -> source.append(' ').append(path));
        junitReports.keySet().forEach(path -> source.append(' ').append(path));
        String text = source.toString().toLowerCase(Locale.ROOT);
        Set<String> signals = new java.util.LinkedHashSet<>();
        addSignal(signals, "maven", text, "mvn ", "pom.xml", "surefire", "failsafe");
        addSignal(signals, "gradle", text, "gradlew", "build.gradle", "test-results/test");
        addSignal(signals, "node", text, "npm ", "yarn ", "pnpm ", "jest", "node_modules");
        addSignal(signals, "python", text, "pytest", "python ", "tox.ini", "junitxml");
        addSignal(signals, "go", text, "go test", "go.mod");
        addSignal(signals, "dotnet", text, "dotnet ", ".csproj", "test-results.trx");
        addSignal(signals, "rust", text, "cargo ", "cargo.toml");
        addSignal(signals, "docker", text, "docker ", "buildah ", "kaniko", "testcontainers");
        if (signals.isEmpty()) {
            signals.add("generic");
        }
        return signals;
    }

    private void addSignal(Set<String> signals, String signal, String source, String... markers) {
        for (String marker : markers) {
            if (source.contains(marker)) {
                signals.add(signal);
                return;
            }
        }
    }

    private PipelineGraph collectGraph(String projectId, Pipeline root) {
        List<PipelineNode> nodes = new ArrayList<>();
        List<PipelineEdge> edges = new ArrayList<>();
        ArrayDeque<PipelineNode> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        PipelineNode rootNode = new PipelineNode(projectId, root, 0);
        nodes.add(rootNode);
        queue.add(rootNode);
        seen.add(pipelineKey(projectId, root.id()));
        boolean truncated = false;
        while (!queue.isEmpty()) {
            PipelineNode current = queue.removeFirst();
            int remainingCapacity = maxPipelines - nodes.size();
            GitlabPage<PipelineBridge> bridges;
            try {
                bridges = gitlab.getPipelineBridges(
                        current.projectId(),
                        String.valueOf(current.pipeline().id()),
                        Math.max(1, remainingCapacity));
            } catch (GitlabNotFoundException unavailable) {
                continue;
            }
            if (bridges == null) {
                continue;
            }
            truncated |= bridges.truncated();
            for (PipelineBridge bridge : bridges.items()) {
                Pipeline downstream = bridge.downstreamPipeline();
                if (downstream == null || downstream.id() == null) {
                    continue;
                }
                String downstreamProjectId = downstream.projectId() == null
                        ? current.projectId()
                        : String.valueOf(downstream.projectId());
                edges.add(new PipelineEdge(
                        current.projectId(),
                        current.pipeline().id(),
                        downstreamProjectId,
                        downstream.id(),
                        bridge.id(),
                        bridge.name()));
                String key = pipelineKey(downstreamProjectId, downstream.id());
                if (seen.contains(key)) {
                    continue;
                }
                if (current.depth() >= maxPipelineDepth || nodes.size() >= maxPipelines) {
                    truncated = true;
                    continue;
                }
                PipelineNode child = new PipelineNode(
                        downstreamProjectId,
                        downstream,
                        current.depth() + 1);
                seen.add(key);
                nodes.add(child);
                queue.addLast(child);
            }
        }
        return new PipelineGraph(nodes, edges, truncated);
    }

    private String pipelineKey(String projectId, Long pipelineId) {
        return projectId + ":" + pipelineId;
    }

    private void collectTrace(
            String projectId,
            Job job,
            PipelineCollectionOptions options,
            Map<Long, String> traces
    ) {
        if (!options.includeTraces()) {
            return;
        }
        traces.put(job.id(), gitlab.getJobTraceTail(
                projectId,
                String.valueOf(job.id()),
                Math.max(1, options.maxTraceBytes())));
    }

    private void collectArtifacts(
            String projectId,
            Job job,
            PipelineCollectionOptions options,
            Map<Long, List<ArtifactFile>> artifacts,
            Map<String, String> junitReports
    ) {
        if (!options.includeArtifacts()) {
            return;
        }
        List<ArtifactFile> files;
        try {
            files = gitlab.listJobArtifacts(
                    projectId,
                    String.valueOf(job.id()),
                    null,
                    true,
                    new GitlabPageRequest(1, Math.max(1, options.maxArtifactFilesPerJob())));
        } catch (GitlabNotFoundException noArtifacts) {
            return;
        }
        artifacts.put(job.id(), files);
        for (ArtifactFile file : files) {
            if (junitReports.size() >= Math.max(1, options.maxJunitReports())
                    || !isJunitReport(file.path())) {
                continue;
            }
            String content = gitlab.getJobArtifactFile(
                    projectId,
                    String.valueOf(job.id()),
                    file.path(),
                    Math.max(1, options.maxReportBytes()));
            if (StringUtils.hasText(content)) {
                junitReports.put(job.id() + ":" + file.path(), content);
            }
        }
    }

    private boolean isJunitReport(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        return normalized.endsWith(".xml")
                && (fileName.startsWith("test-")
                || normalized.contains("junit")
                || normalized.contains("surefire-reports")
                || normalized.contains("test-results")
                || normalized.contains("pytest")
                || normalized.contains("jest"));
    }

    private Pipeline resolvePipeline(String projectId, String pipelineId, String mergeRequestIid) {
        if (StringUtils.hasText(pipelineId)) {
            return gitlab.getPipeline(projectId, pipelineId);
        }
        if (StringUtils.hasText(mergeRequestIid)) {
            List<Pipeline> pipelines = gitlab.listMergeRequestPipelines(
                    projectId,
                    mergeRequestIid,
                    new GitlabPageRequest(1, 20));
            return pipelines.stream()
                    .filter(pipeline -> "failed".equals(pipeline.status())
                            || "canceled".equals(pipeline.status()))
                    .findFirst()
                    .or(() -> pipelines.stream().findFirst())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No pipelines found for merge request: " + mergeRequestIid));
        }
        throw new IllegalArgumentException("Either pipelineId or mergeRequestIid must be set");
    }
}
