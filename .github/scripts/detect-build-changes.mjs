import {pathToFileURL} from "node:url";

const sharedBuildPaths = [
    /^src\//,
    /^pom\.xml$/,
    /^LICENSE$/,
    /^NOTICE$/,
    /^\.github\/workflows\/ci\.yml$/
];
const javaBuildPaths = [
    ...sharedBuildPaths,
    /^\.mvn\//,
    /^mvnw(?:\.cmd)?$/,
    /^pmd\.ruleset\.xml$/
];
const dockerBuildPaths = [
    ...sharedBuildPaths,
    /^\.dockerignore$/,
    /^Dockerfile$/,
    /^certs\//
];

export function detectBuildChanges(paths) {
    return {
        java: matchesAny(paths, javaBuildPaths),
        docker: matchesAny(paths, dockerBuildPaths)
    };
}

function matchesAny(paths, patterns) {
    return paths.some(path => patterns.some(pattern => pattern.test(path)));
}

async function main() {
    let input = "";
    for await (const chunk of process.stdin) {
        input += chunk;
    }

    const paths = input.split("\0").filter(Boolean);
    const changes = detectBuildChanges(paths);

    process.stdout.write(`java=${changes.java}\n`);
    process.stdout.write(`docker=${changes.docker}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    await main();
}
