# syntax=docker/dockerfile:1

# ── Stage 1: build the jar with Maven ──────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B

# ── Stage 2: run the jar on a slim JRE ─────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS production
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
