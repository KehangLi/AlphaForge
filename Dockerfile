# syntax=docker/dockerfile:1

# Compile JVM bytecode on the builder's native architecture, including on Apple Silicon.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-noble@sha256:06c1f157dacf6b68198bd9a4186dd4c34ef24e65d7bbbe2a0cf8e62571ad38c9 AS build
WORKDIR /workspace

COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/maven-wrapper.properties
COPY --chmod=0755 mvnw ./mvnw
COPY pom.xml ./pom.xml
COPY src/main/ ./src/main/

# Tests run separately with Docker available; an image build needs no database or secrets.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-noble@sha256:0d57a8e90c2bb844c5a8e1b2b71e11eff06dad7edcfd803cd21ed4ae203b2515 AS runtime
ARG BUILD_VERSION=development
ARG BUILD_REVISION=unknown
LABEL org.opencontainers.image.source="https://github.com/KehangLi/AlphaForge" \
      org.opencontainers.image.version="$BUILD_VERSION" \
      org.opencontainers.image.revision="$BUILD_REVISION"
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/*.jar /app/application.jar

# Container deployments default to production; connection details are supplied at runtime.
ENV SPRING_PROFILES_ACTIVE=prod \
    ALPHAFORGE_RELEASE_VERSION="$BUILD_VERSION" \
    ALPHAFORGE_RELEASE_COMMIT="$BUILD_REVISION"
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
