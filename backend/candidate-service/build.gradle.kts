// candidate-service module (docs/architecture.md §3.1).
// Plugin, Java 21 toolchain, Spring Boot 4.1.1 platform/BOM and shared
// dependencies are declared once in ../build.gradle.kts and inherited here.
description = "PullHire candidate-service — profile, resume/PDF, privacy, card"

dependencies {
    // Shared event mechanics (S2): transactional outbox, envelope, correlation,
    // consumer retry/DLQ. candidate-service publishes card/contact events and
    // consumes invitation.accepted (contact release).
    implementation(project(":event-lib"))

    // Persistence: JdbcTemplate + Flyway (docs/data_model.md §1, per-schema migrations).
    // Boot 4 ships Flyway autoconfiguration in spring-boot-starter-flyway (not in flyway-core alone).
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Events (docs/architecture.md §4.2): publish to and consume from pullhire.events.
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // Observability (S2, docs/architecture.md §7.1): health probes + Prometheus /metrics.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // JWT RS256 / JWKS (docs/stack.md §2): api at compile time, impl/jackson at runtime.
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // Resume PDF rendering (S3). Explicit version: PDFBox is not BOM-managed.
    implementation("org.apache.pdfbox:pdfbox:3.0.5")
}

tasks.withType<Test>().configureEach {
    // Hand the committed contract schemas to the payload conformance test.
    systemProperty(
        "pullhire.contracts.dir",
        rootProject.projectDir.parentFile.resolve("contracts/events").absolutePath,
    )
}
