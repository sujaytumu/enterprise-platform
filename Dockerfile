# Root Dockerfile for the consolidated backend service.
# Deliberately placed at repo root (not backend/Dockerfile) so Render can
# build it with zero extra configuration — no "root directory" setting needed
# in the dashboard, and it works directly with automated Render API deploys
# that don't support a custom build context.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q dependency:go-offline -B
COPY backend/src ./src
RUN mvn -q clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/app.jar app.jar
USER app
EXPOSE 8080

# Used by plain Docker / compose. (Render ignores this and uses
# healthCheckPath from render.yaml instead.)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- "http://localhost:${PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
