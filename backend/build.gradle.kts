// PullHire backend — Gradle multi-project root (docs/deployment.md §2).
// Spring Boot 4.1.1 + Java 21; versions inherited by all eight service modules
// through the Spring Boot platform/BOM defined below (docs/stack.md §2).
plugins {
    java
    id("org.springframework.boot") version "4.1.1" apply false
}

allprojects {
    group = "me.yuugao.pullhire"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencies {
        // Single source of managed versions for every module.
        implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
        testImplementation("org.springframework.boot:spring-boot-starter-test")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

// event-lib and idempotency-lib are plain shared libraries: keep the ordinary
// `jar`, no executable Spring Boot fat jar. They still inherit the BOM + Java 21
// toolchain above.
listOf(project(":event-lib"), project(":idempotency-lib")).forEach { lib ->
    lib.tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
        enabled = false
    }
    lib.tasks.named<Jar>("jar") {
        enabled = true
    }
}
