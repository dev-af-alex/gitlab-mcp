const branch = process.env.PR_BRANCH ?? "";
const title = process.env.PR_TITLE ?? "";
const body = process.env.PR_BODY ?? "";

const branchPattern = /^(feat|fix|docs|refactor|test|ci|build|perf|revert|chore)\/[a-z0-9][a-z0-9._-]*$/;
const titlePattern = /^(feat|fix|docs|refactor|test|ci|build|perf|revert|chore)(\([a-z0-9][a-z0-9._/-]*\))?!?: \S.+$/;
const requiredSections = ["Summary", "Testing", "Compatibility"];
const errors = [];

if (!branchPattern.test(branch)) {
    errors.push(
        `Branch '${branch}' must match '<type>/<short-description>'; see CONTRIBUTING.md for allowed types.`
    );
}

if (!titlePattern.test(title)) {
    errors.push(`Pull request title '${title}' must use the Conventional Commit format described in CONTRIBUTING.md.`);
}

for (const section of requiredSections) {
    const content = sectionContent(body, section);
    if (content.length === 0) {
        errors.push(`Pull request section '${section}' must contain a response.`);
    }
}

if (errors.length > 0) {
    for (const error of errors) {
        console.error(`::error::${error}`);
    }
    process.exit(1);
}

console.log("Pull request metadata follows CONTRIBUTING.md.");

function sectionContent(markdown, section) {
    const normalizedMarkdown = markdown.replace(/\r\n?/g, "\n");
    const escapedSection = section.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const match = normalizedMarkdown.match(new RegExp(`^ {0,3}##[ \\t]+${escapedSection}[ \\t]*$`, "im"));
    if (match === null || match.index === undefined) {
        return "";
    }

    const contentStart = match.index + match[0].length;
    const remaining = normalizedMarkdown.slice(contentStart);
    const nextHeading = remaining.search(/^ {0,3}##[ \t]+/m);
    const content = nextHeading === -1 ? remaining : remaining.slice(0, nextHeading);

    return content.replace(/<!--[\s\S]*?-->/g, "").trim();
}
