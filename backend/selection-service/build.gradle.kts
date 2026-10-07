// selection-service module (docs/architecture.md §3.1).
// Plugin, Java 21 toolchain, Spring Boot 4.1.1 platform/BOM and shared
// dependencies are declared once in ../build.gradle.kts and inherited here.
// Observability (docs/architecture.md §7.1): expose Prometheus /metrics.
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
}
description = "PullHire selection-service — taxonomy, categories, ranking, selection with explanations"
