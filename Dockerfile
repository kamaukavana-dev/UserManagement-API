# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jre-jammy AS production
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
