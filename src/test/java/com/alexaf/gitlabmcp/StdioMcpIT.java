package com.alexaf.gitlabmcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StdioMcpIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Test
    void respondsToInitializeWithoutNonProtocolStdout() throws Exception {
        Process process = new ProcessBuilder(
                        javaExecutable(), "-jar", applicationJar().toString())
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        process.getOutputStream().write("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke-test","version":"1.0"}}}
                """.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().flush();

        BufferedReader stdout =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        String firstLine =
                CompletableFuture.supplyAsync(() -> readLine(stdout)).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        process.getOutputStream().close();
        boolean exited = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
        }
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(exited)
                .as("server exits after stdin closes; stderr=%s", stderr)
                .isTrue();
        assertThat(process.exitValue()).as("stderr=%s", stderr).isZero();
        JsonNode response = new ObjectMapper().readTree(firstLine);
        assertThat(response.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(response.path("id").asInt()).isEqualTo(1);
        assertThat(response.path("result").path("serverInfo").path("name").asText())
                .isEqualTo("gitlab-mcp");
        assertThat(stdout.readLine())
                .as("stdout must contain protocol messages only")
                .isNull();
    }

    private String readLine(BufferedReader reader) {
        try {
            return reader.readLine();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read MCP stdout", e);
        }
    }

    private Path applicationJar() throws Exception {
        Path target = Path.of("target");
        try (var files = Files.list(target)) {
            return files.filter(path -> path.getFileName().toString().startsWith("gitlab-mcp-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Packaged application JAR was not found"));
        }
    }

    private String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }
}
