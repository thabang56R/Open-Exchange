# --- build stage -------------------------------------------------------------
FROM gradle:8.10-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle bootJar --no-daemon

# --- runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
