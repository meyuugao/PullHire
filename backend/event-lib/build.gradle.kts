// event-lib (S2): shared transactional-outbox / event-envelope mechanics for
// every business service. Library only — no main class, no bootJar (see
// ../build.gradle.kts). All versions come from the Spring Boot 4.1.1 BOM
// inherited through the root subprojects {} block; no artifact is pinned here.
description = "PullHire event-lib — envelope, transactional outbox, consumer retry/DLQ, correlation"

dependencies {
    // Broker: RabbitTemplate, listener container factory, publisher confirms.
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    // Outbox persistence in the service's own schema via JdbcTemplate.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // Servlet OncePerRequestFilter for X-Request-Id -> MDC correlation_id.
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test>().configureEach {
    // E1: hand the committed contract schemas to the envelope conformance test.
    systemProperty(
        "pullhire.contracts.dir",
        rootProject.projectDir.parentFile.resolve("contracts/events").absolutePath,
    )
}
