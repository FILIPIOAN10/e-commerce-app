# ── Build stage ──
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -B -DskipTests

# ── Runtime stage ──
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring && \
    mkdir -p images && chown -R spring:spring /app

USER spring

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

# Let the JVM size its heap to the container's memory limit rather than the
# host's — the usual cause of a Java-in-Docker OOMKill.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# busybox wget ships in the alpine image; --spider makes a HEAD-style check.
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
