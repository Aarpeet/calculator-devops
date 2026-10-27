# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1 (builder): compile and package the executable Spring Boot JAR
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copy only the POM first so the dependency layer is cached and reused until
# pom.xml changes; source edits then rebuild in seconds.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------------------------------------------------------------------------
# Stage 2 (runtime): slim JRE image that contains only the JAR
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.title="calculator-api" \
      org.opencontainers.image.description="Calculator REST API (Spring Boot 3, Java 17)" \
      org.opencontainers.image.source="https://github.com/Aarpeet/calculator-devops"

# curl for health checks; dedicated unprivileged user and group
RUN apk add --no-cache curl \
    && addgroup -S appgroup \
    && adduser -S -G appgroup -H -s /sbin/nologin appuser

WORKDIR /app

# The JAR stays root-owned (read-only for appuser), so the running process
# cannot modify its own code.
COPY --from=builder /build/target/calculator-api.jar app.jar

USER appuser

ENV SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
