// ─────────────────────────────────────────────────────────────────────────────
// Stack: Spring Boot 4.0.5 · Spring Cloud 2025.1.1 (Oakwood) · Java 25
// Las versiones de las dependencias Spring las gestiona el BOM del build.gradle.kts raíz.
// ─────────────────────────────────────────────────────────────────────────────
springBoot {
    buildInfo()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("io.r2dbc:r2dbc-h2")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-stream-kafka")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-zipkin")

    // Binder en memoria para tests: sustituye Kafka sin necesitar broker real
    testImplementation("org.springframework.cloud:spring-cloud-stream-test-binder")
    // WebTestClient para tests de integración reactivos
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    // StepVerifier para verificar pipelines reactivos en tests
    testImplementation("io.projectreactor:reactor-test")
}
