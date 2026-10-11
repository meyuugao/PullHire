# syntax=docker/dockerfile:1
# Packaging (docs/deployment.md §2): jar is built locally on the host
# (`./gradlew bootJar`, JDK 21); the image only packages the artifact.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY auth-service/build/libs/auth-service-0.1.0.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

