// idempotency-lib (ST-9.1): reusable Idempotency-Key middleware (docs/api.md §1–§2).
// Library only — no main class, no bootJar (see ../build.gradle.kts). All versions
// come from the Spring Boot 4.1.1 BOM inherited through the root subprojects {}
// block; no artifact is pinned here.
description = "PullHire idempotency-lib — Idempotency-Key filter, JDBC result store"

dependencies {
    // Filter + result-store persistence in the service's own schema via JdbcTemplate.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // Servlet OncePerRequestFilter for the Idempotency-Key middleware.
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
