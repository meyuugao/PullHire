# syntax=docker/dockerfile:1
# Multi-stage build (docs/deployment.md §2.2): eclipse-temurin:21-jdk -> :21-jre.
# Build context is the Gradle multi-project root ./backend.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat gradle.properties ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY auth-service/build.gradle.kts auth-service/build.gradle.kts
COPY candidate-service/build.gradle.kts candidate-service/build.gradle.kts
COPY fsp-service/build.gradle.kts fsp-service/build.gradle.kts
COPY assessment-service/build.gradle.kts assessment-service/build.gradle.kts
COPY selection-service/build.gradle.kts selection-service/build.gradle.kts
COPY employer-service/build.gradle.kts employer-service/build.gradle.kts
COPY outreach-service/build.gradle.kts outreach-service/build.gradle.kts
COPY notification-service/build.gradle.kts notification-service/build.gradle.kts
COPY event-lib/build.gradle.kts event-lib/build.gradle.kts
COPY event-lib/src event-lib/src
COPY fsp-service/src fsp-service/src

RUN chmod +x gradlew && ./gradlew :fsp-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/fsp-service/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
