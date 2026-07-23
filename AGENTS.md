# Instructions for AI Agents

## Project

GitLab MCP is a read-only stdio MCP server for GitLab merge requests, pipelines, job artifacts, and CI diagnostics.
It is a Java 21 Maven project built with Spring Boot and Spring AI. GitLab 15.1 is the compatibility baseline.

## Working Principles

- Think before coding: inspect the relevant code, state material assumptions, and surface ambiguous requirements or
  trade-offs.
- Prefer the smallest solution that satisfies the request. Do not add speculative abstractions, configuration, or
  unrelated cleanup.
- Make surgical changes. Match nearby style and keep every changed line traceable to the task.
- Define a verifiable outcome for non-trivial work, then implement and run the narrowest relevant checks.
- Never mutate GitLab or other external systems while developing or testing this server.

## Architecture and Invariants

- `domain/` contains transport-independent models; `application/` orchestrates use cases through interfaces in
  `port/`; `adapter/` implements external integrations; `tool/` exposes thin MCP-facing entry points.
- Keep GitLab REST details, DTOs, version checks, and capability fallbacks behind `GitlabGateway`.
- One process connects to one GitLab instance. Preserve runtime configuration and GitLab-version compatibility.
- MCP tool names use `gitlab_` plus `snake_case`, and every exposed operation remains read-only.
- Treat stdout as MCP protocol output only. Never log there or return unredacted credentials.
- Preserve configured bounds for pagination, pipeline traversal, traces, artifacts, downloads, and retries.

## Build and Test

```bash
mvn test          # unit tests
mvn clean verify  # full build, including integration tests
docker build -t gitlab-mcp:latest .
```

Tests live under `src/test/java/com/alexaf/gitlabmcp/` and mirror production packages. Name unit tests `*Test` and
integration tests `*IT`. Add a focused regression test for behavior changes. Mock GitLab HTTP interactions; tests must
not require a token, network access, or a mutable GitLab instance.

## Conventions

Use four-space indentation, explicit imports, constructor injection, and immutable data where practical. Follow
existing naming and structure rather than reformatting adjacent code. Never commit tokens or private certificates.
When tools or configuration change, update both `README.md` and `docker-mcp/server.json` where applicable.
