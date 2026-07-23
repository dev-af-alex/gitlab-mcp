import assert from "node:assert/strict";
import test from "node:test";

import {detectBuildChanges} from "./detect-build-changes.mjs";

test("skips builds for documentation-only changes", () => {
    assert.deepEqual(
        detectBuildChanges(["README.md", "CONTRIBUTING.md", "AGENTS.md"]),
        {java: false, docker: false}
    );
});

test("runs both builds for application changes", () => {
    assert.deepEqual(
        detectBuildChanges(["src/main/java/com/alexaf/gitlabmcp/GitlabMcpApplication.java"]),
        {java: true, docker: true}
    );
});

test("runs only the affected build for build-tool changes", () => {
    assert.deepEqual(detectBuildChanges(["mvnw"]), {java: true, docker: false});
    assert.deepEqual(detectBuildChanges(["Dockerfile"]), {java: false, docker: true});
});

test("runs both builds when the CI workflow changes", () => {
    assert.deepEqual(
        detectBuildChanges([".github/workflows/ci.yml"]),
        {java: true, docker: true}
    );
});
