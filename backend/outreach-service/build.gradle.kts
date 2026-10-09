// outreach-service module (docs/architecture.md §3.1).
// Plugin, Java 21 toolchain, Spring Boot 4.1.1 platform/BOM and shared
// dependencies are declared once in ../build.gradle.kts and inherited here.
description = "PullHire outreach-service — LHH invitations, lifecycle, contact release"

dependencies {
    // Shared event mechanics (S2): transactional outbox + envelope. outreach-service
    // publishes invitation events via the outbox relay.
    implementation(project(":event-lib"))

    // Persistence: JdbcTemplate + Flyway (docs/data_model.md §1, per-schema migrations).
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Events (docs/architecture.md §4.2): publish to pullhire.events via the outbox relay.
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // Observability (docs/architecture.md §7.1): health probes + Prometheus /metrics.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // JWT RS256 / JWKS (docs/stack.md §2): api at compile time, impl/jackson at runtime.
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
}
