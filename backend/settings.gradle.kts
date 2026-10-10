pluginManagement {
    repositories {
        // Maven Central first: the Spring Boot plugin marker is also published
        // there (org.springframework.boot.gradle.plugin), so plugin resolution
        // does not depend solely on the Gradle Plugin Portal.
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "pullhire-backend"

// Exactly the eight business services (docs/architecture.md §1.1).
// NOT Gradle modules: api-gateway (nginx), frontend (Next.js), seed (owner S12).
include(
    "auth-service",
    "candidate-service",
    "fsp-service",
    "assessment-service",
    "selection-service",
    "employer-service",
    "outreach-service",
    "notification-service",
)

// Shared event contract/mechanics library (S2): envelope, outbox, consumer
// retry/DLQ, correlation MDC. A plain library — NOT a business service, so it
// is intentionally excluded from the eight-service list above.
include("event-lib")

// Shared Idempotency-Key middleware library (ST-9.1): filter + JDBC result
// store. A plain library — NOT a business service, same as event-lib above.
include("idempotency-lib")
