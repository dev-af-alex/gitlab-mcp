# Contributing to GitLab MCP

GitLab MCP uses a trunk-based workflow. `main` is the only long-lived branch, and every change reaches it through a
pull request.

## Prerequisites

- JDK 21 or newer
- Docker, only when changing or verifying the container image

Use the Maven Wrapper included in the repository:

```bash
./mvnw test
./mvnw clean verify
```

On Windows, run the equivalent commands with `mvnw.cmd`.

## Create a Branch

Update `main`, then create a short-lived branch:

```bash
git switch main
git pull --ff-only
git switch -c feat/short-description
```

Branch names must use lowercase letters, digits, dots, underscores, and hyphens after one of these prefixes:

- `feat/` for new behavior
- `fix/` for bug fixes
- `docs/` for documentation
- `refactor/` for behavior-preserving code changes
- `test/` for test-only changes
- `ci/` for CI changes
- `build/` for build changes
- `perf/` for performance improvements
- `revert/` for reversions
- `chore/` for maintenance

## Commits

Keep commits focused enough to review, but intermediate commits do not need to follow a naming convention. Pull
requests are squash-merged, so the pull request title becomes the commit recorded on `main`.

Never commit tokens, private certificates, generated `target/` content, or credentials in examples and fixtures.

## Open a Pull Request

Use a Conventional Commit-style title:

```text
feat: add pipeline artifact diagnostics
fix(gitlab): handle a missing job trace
docs!: replace the configuration format
```

Allowed types are `feat`, `fix`, `docs`, `refactor`, `test`, `ci`, `build`, `perf`, `revert`, and `chore`. A scope and
the `!` breaking-change marker are optional.

Complete the Summary, Testing, and Compatibility sections in the pull request template. Use `Not applicable` when a
section genuinely does not apply; do not leave template comments as the only content.

Before requesting review:

1. Run `./mvnw clean verify`.
2. Build the image with `docker build -t gitlab-mcp:latest .` when container behavior changes.
3. Add a focused regression test for behavior changes.
4. Update `README.md` and `docker-mcp/server.json` when exposed tools or their configuration change.
5. Keep tests offline and mock GitLab HTTP interactions.

CI validates the branch name, pull request title, required template sections, commit topology, Java builds, and the
multi-platform Docker image.

## Review and Merge

- Address review comments and resolve all conversations.
- Only the repository owner merges pull requests.
- Direct pushes, force pushes, and deletion of `main` are blocked.
- Pull requests are squash-merged after all required checks pass.
- The source branch is deleted after merge.
