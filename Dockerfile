# Multi-stage Docker build for Caliber Core (Spring Boot 4 / Java 21)
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# GitHub Packages authentication for private dependencies
ARG GITHUB_ACTOR=keerthanmahesh
ARG GITHUB_TOKEN=""
ENV GITHUB_ACTOR=${GITHUB_ACTOR}
ENV GITHUB_TOKEN=${GITHUB_TOKEN}

# Copy Gradle wrapper and build configuration
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle gradle.properties ./

# Ensure gradlew has executable permissions
RUN chmod +x ./gradlew

# Copy source files and build executable jar
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# Production runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root system user and group for security
RUN groupadd -r caliber && useradd -r -g caliber caliber

# Copy executable jar from builder stage
COPY --from=builder /workspace/build/libs/caliber-core-*.jar app.jar
RUN chown -R caliber:caliber /app

USER caliber

# Default port matching Render PORT env var or 8087
ENV PORT=8087
EXPOSE ${PORT}

# JVM tuning options for containerized environments
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
