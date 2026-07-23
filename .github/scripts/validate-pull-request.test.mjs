import assert from "node:assert/strict";
import {spawnSync} from "node:child_process";
import {fileURLToPath} from "node:url";
import test from "node:test";

const validatorPath = fileURLToPath(new URL("validate-pull-request.mjs", import.meta.url));

test("accepts Markdown headings indented by up to three spaces", () => {
    const result = validate(`
   ## Summary

Summary response.

  ## Testing

Testing response.

 ## Compatibility

Compatibility response.
`);

    assert.equal(result.status, 0, result.stderr);
});

test("rejects headings indented as a code block", () => {
    const result = validate(`
    ## Summary

Summary response.

    ## Testing

Testing response.

    ## Compatibility

Compatibility response.
`);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /Pull request section 'Summary' must contain a response\./);
});

function validate(body) {
    return spawnSync(process.execPath, [validatorPath], {
        encoding: "utf8",
        env: {
            ...process.env,
            PR_BODY: body,
            PR_BRANCH: "ci/markdown-headings",
            PR_TITLE: "ci: accept indented Markdown headings"
        }
    });
}
