# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --version

COPY src src
RUN ./gradlew installDist --no-daemon

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

COPY --from=build /workspace/build/install/fpl-bot/ ./
COPY db/migrations db/migrations

EXPOSE 8081
ENTRYPOINT ["./bin/fpl-bot"]
