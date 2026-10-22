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

FROM eclipse-temurin:21-jre-noble@sha256:35501d3f93173b420ac9001658eb68157e813316b865087e345c187fca422c4c AS runtime
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/*.jar /app/application.jar

# Container deployments default to production; connection details are supplied at runtime.
ENV SPRING_PROFILES_ACTIVE=prod
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
