# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build
# Uses a full JDK image with Maven to compile and package the application.
# The fat JAR is then copied to a lean runtime image.
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

# Copy dependency manifests first so Docker can cache the Maven layer.
COPY pom.xml .
# Download all dependencies (offline-friendly cache layer).
RUN mvn dependency:go-offline -B --no-transfer-progress 2>/dev/null || true

# Copy the rest of the source tree and build.
COPY src ./src
RUN mvn package -DskipTests -B --no-transfer-progress

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime
# Uses a minimal JRE image — no compiler, no Maven, smaller attack surface.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security hardening.
RUN addgroup -S mdes && adduser -S mdes -G mdes

WORKDIR /app

# Copy the packaged JAR from the build stage.
COPY --from=builder /workspace/target/mdes-*.jar app.jar

# Switch to non-root user.
USER mdes

# Expose the TSP Simulator service port (matches docker-compose.yml).
EXPOSE 8081

# JVM tuning for containerised environments:
#   -XX:+UseContainerSupport          — respects cgroup memory/CPU limits
#   -XX:MaxRAMPercentage=75.0         — caps heap to 75 % of container RAM
#   -XX:+ExitOnOutOfMemoryError       — fail-fast instead of limping along
#   -Djava.security.egd=...           — faster SecureRandom startup
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
