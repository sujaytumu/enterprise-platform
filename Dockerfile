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
COPY --from=build /app/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
