# GitLab MCP

A read-only Model Context Protocol server for GitLab merge requests, pipelines, job traces, artifacts, and CI failure
diagnostics. It uses Spring Boot and Spring AI and communicates with MCP clients over stdio.

## Requirements

- JDK 21 or newer and Maven 3.9+ for a native build
- Docker for the container image
- Docker Desktop with MCP Toolkit enabled for Docker MCP profiles
- A GitLab Personal Access Token with the `read_api` scope

## Tools

- `gitlab_get_current_user`
- `gitlab_search_projects`
- `gitlab_get_project`
- `gitlab_list_merge_requests`
- `gitlab_get_merge_request`
- `gitlab_get_merge_request_changes`
- `gitlab_get_merge_request_commits`
- `gitlab_get_merge_request_discussions`
- `gitlab_get_merge_request_pipelines`
- `gitlab_get_pipeline`
- `gitlab_list_pipeline_jobs`
- `gitlab_get_job_trace`
- `gitlab_get_job_trace_tail`
- `gitlab_get_job_trace_matches`
- `gitlab_list_job_artifacts`
- `gitlab_find_job_artifact_files`
- `gitlab_get_job_artifact_file`
- `gitlab_extract_job_failure_summary`
- `gitlab_analyze_job_surefire_reports`
- `gitlab_analyze_failed_pipeline`
- `gitlab_analyze_mr_pipeline_failure`

All tools are declared read-only and never mutate GitLab state.

## Resources

- `gitlab://projects/{projectId}/pipelines/{pipelineId}/summary`
- `gitlab://projects/{projectId}/jobs/{jobId}/trace`
- `gitlab://projects/{projectId}/jobs/{jobId}/artifacts/{artifactPath}`

Project and artifact paths containing `/` must be URL-encoded in resource URIs. Trace and artifact reads are bounded.
Returned text is redacted for common credential formats before it reaches the MCP client.

## Configuration

The server is configured with environment variables:

| Variable                  | Required | Default              | Description                                 |
|---------------------------|----------|----------------------|---------------------------------------------|
| `GITLAB_TOKEN`            | yes      | —                    | Personal Access Token with `read_api` scope |
| `GITLAB_URL`              | no       | `https://gitlab.com` | GitLab base URL                             |
| `GITLAB_ALLOWED_PROJECTS` | no       | empty                | Comma-separated project IDs or paths        |
| `GITLAB_DEFAULT_PER_PAGE` | no       | `20`                 | Default page size                           |
| `GITLAB_MAX_PER_PAGE`     | no       | `100`                | Maximum page size                           |

Project paths in `GITLAB_ALLOWED_PROJECTS` may be plain (`group/project`) or URL-encoded (`group%2Fproject`). Leaving
the variable empty allows every project visible to the token, so setting an allow-list is recommended.

Never commit a token. Keep it in the MCP client's secret storage or process environment.

## Run directly on the system

Build and test:

```bash
mvn clean package
```

Start the stdio server:

```bash
read -rsp "GitLab token: " GITLAB_TOKEN
export GITLAB_TOKEN
export GITLAB_URL=https://gitlab.com
export GITLAB_ALLOWED_PROJECTS=group/project
java -jar target/gitlab-mcp-0.1.0.jar
unset GITLAB_TOKEN
```

The process waits for MCP messages on stdin and writes protocol messages to stdout. Application logging is disabled
because any non-protocol stdout output would break stdio transport.

For development, run it without packaging:

```bash
GITLAB_TOKEN=your-token mvn spring-boot:run
```

A generic MCP client entry for the packaged JAR looks like this:

```json
{
  "mcpServers": {
    "gitlab": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/gitlab-mcp-0.1.0.jar"
      ],
      "env": {
        "GITLAB_URL": "https://gitlab.com",
        "GITLAB_TOKEN": "configure-this-with-your-client-secret-store",
        "GITLAB_ALLOWED_PROJECTS": "group/project"
      }
    }
  }
}
```

Use the configuration shape and secret mechanism supported by your MCP client.

## Run with Docker

The Docker build is self-contained and does not require a prebuilt JAR:

```bash
docker build -t gitlab-mcp:local .
```

Run the container as a plain stdio MCP server:

```bash
docker run --rm -i \
  -e GITLAB_TOKEN \
  -e GITLAB_URL=https://gitlab.com \
  -e GITLAB_ALLOWED_PROJECTS=group/project \
  gitlab-mcp:local
```

For a self-hosted GitLab instance using a private certificate authority, place one or more `.crt` files in `certs/`
before building. The Dockerfile imports them into the image's Java trust store. Certificates in that directory are
ignored by Git to prevent accidental publication.

## Docker MCP Toolkit

Build the `gitlab-mcp:local` image first, then store the GitLab token in Docker MCP's secret store:

```bash
read -rsp "GitLab token: " GITLAB_TOKEN
printf '%s' "$GITLAB_TOKEN" |
  docker mcp secret set gitlab-mcp.personal_access_token
unset GITLAB_TOKEN
```

Docker MCP requires local server definitions to be stored under its catalogs directory. Copy the included definition
there and create a profile:

```bash
mkdir -p "$HOME/.docker/mcp/catalogs"
cp docker-mcp/server.json "$HOME/.docker/mcp/catalogs/gitlab-mcp.json"

docker mcp profile create \
  --name gitlab-mcp \
  --server file://gitlab-mcp.json
```

The profile defaults to `https://gitlab.com`. Configure a self-hosted instance or restrict visible projects when needed:

```bash
docker mcp profile config gitlab-mcp \
  --set gitlab-mcp.url=https://gitlab.example.com \
  --set gitlab-mcp.allowed_projects=group/project
```

List the exposed tools:

```bash
docker mcp tools ls \
  --gateway-arg --profile \
  --gateway-arg gitlab-mcp
```

Call a tool:

```bash
docker mcp tools call gitlab_get_current_user \
  --gateway-arg --profile \
  --gateway-arg gitlab-mcp
```

Connect a supported client directly to the profile, for example Codex:

```bash
docker mcp client connect codex --profile gitlab-mcp
```

For a client configured manually over stdio, use:

```json
{
  "servers": {
    "MCP_DOCKER": {
      "command": "docker",
      "args": [
        "mcp",
        "gateway",
        "run",
        "--profile",
        "gitlab-mcp"
      ],
      "type": "stdio"
    }
  }
}
```
