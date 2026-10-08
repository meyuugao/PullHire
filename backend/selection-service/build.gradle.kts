// selection-service module (docs/architecture.md §3.1).
// Plugin, Java 21 toolchain, Spring Boot 4.1.1 platform/BOM and shared
// dependencies are declared once in ../build.gradle.kts and inherited here.
// Observability (docs/architecture.md §7.1): expose Prometheus /metrics.
dependencies {
    // Shared event mechanics (S2): transactional outbox, envelope, correlation,
    // consumer retry/DLQ.
    implementation(project(":event-lib"))

    // Persistence: JdbcTemplate + Flyway (docs/data_model.md §1, per-schema migrations).
    // Boot 4 ships Flyway autoconfiguration in spring-boot-starter-flyway (not in flyway-core alone).
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Events (docs/architecture.md §4.2): publish to and consume from pullhire.events.
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
}
description = "PullHire selection-service — taxonomy, categories, ranking, selection with explanations"
