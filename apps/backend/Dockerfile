####
# Multi-stage Dockerfile for Quarkus backend application
# This Dockerfile builds the application and creates a production container with Java 21
#
# Build stage: Compile and package the Quarkus application
# Runtime stage: Run the application with Java 21 LTS
####

# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy all source files
COPY . .

# Build the application (skip tests for faster builds)
RUN mvn package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

ENV LANGUAGE='en_US:en'

# Create a non-root user
RUN addgroup -g 1001 -S quarkus && \
    adduser -u 1001 -S quarkus -G quarkus

WORKDIR /deployments

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --from=builder --chown=1001:1001 /app/target/quarkus-app/lib/ ./lib/
COPY --from=builder --chown=1001:1001 /app/target/quarkus-app/*.jar ./
COPY --from=builder --chown=1001:1001 /app/target/quarkus-app/app/ ./app/
COPY --from=builder --chown=1001:1001 /app/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080
USER 1001

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="quarkus-run.jar"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/q/health/ready || exit 1

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
