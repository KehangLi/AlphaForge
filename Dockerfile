# syntax=docker/dockerfile:1

# Compile JVM bytecode on the builder's native architecture, including on Apple Silicon.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-noble@sha256:107215954359f1ab0be058a247d7c64ed6e69c71c1682d471d38ef751ec8e964 AS build
WORKDIR /workspace

COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/maven-wrapper.properties
COPY --chmod=0755 mvnw ./mvnw
COPY pom.xml ./pom.xml
COPY src/main/ ./src/main/

# Tests run separately with Docker available; an image build needs no database or secrets.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-noble@sha256:161310cd5bd41fd549d19cf9f90b614897cb4a1532555c613b43b7e0628fe5d6 AS runtime
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
