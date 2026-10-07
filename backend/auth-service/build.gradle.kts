// auth-service module (docs/architecture.md §3.1).
// Plugin, Java 21 toolchain, Spring Boot 4.1.1 platform/BOM and shared
// dependencies are declared once in ../build.gradle.kts and inherited here.
description = "PullHire auth-service — identity, JWT, roles, 152-FZ consents"

dependencies {
    // Persistence: JdbcTemplate + Flyway (docs/data_model.md §1, per-schema migrations).
    // Boot 4 ships Flyway autoconfiguration in spring-boot-starter-flyway (not in flyway-core alone).
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Events (docs/architecture.md §4.2): minimal publisher to pullhire.events, no consumers here.
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // Passwords: BCrypt only, without the servlet security filter chain (docs/stack.md §2).
    implementation("org.springframework.security:spring-security-crypto")

    // JWT RS256 / JWKS (docs/stack.md §2): api at compile time, impl/jackson at runtime.
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
}
