FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre

COPY certs/ /tmp/custom-ca-certificates/
RUN set -eu; \
    certificate_index=0; \
    for certificate in /tmp/custom-ca-certificates/*.crt; do \
        [ -f "$certificate" ] || continue; \
        certificate_index=$((certificate_index + 1)); \
        keytool -importcert -noprompt -trustcacerts \
            -alias "custom-ca-${certificate_index}" \
            -file "$certificate" \
            -cacerts \
            -storepass changeit; \
    done; \
    rm -rf /tmp/custom-ca-certificates

WORKDIR /app
COPY --from=build /workspace/target/gitlab-mcp-*.jar /app/app.jar

USER 10001

ENV GITLAB_URL=https://gitlab.com \
    GITLAB_DEFAULT_PER_PAGE=20 \
    GITLAB_MAX_PER_PAGE=100 \
    GITLAB_MAX_JOBS=500 \
    GITLAB_MAX_PIPELINES=20 \
    GITLAB_MAX_PIPELINE_DEPTH=3 \
    GITLAB_CONNECT_TIMEOUT=10s \
    GITLAB_READ_TIMEOUT=60s \
    GITLAB_MAX_DOWNLOAD_BYTES=100000000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
