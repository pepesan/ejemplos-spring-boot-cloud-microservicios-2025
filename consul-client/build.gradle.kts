// ─────────────────────────────────────────────────────────────────────────────
// Stack: Spring Boot 4.0.5 · Spring Cloud 2025.1.1 (Oakwood) · Java 25
// Las versiones de las dependencias Spring las gestiona el BOM del build.gradle.kts raíz.
// io.asyncer:r2dbc-mysql: versión fijada explícitamente (no está en el BOM de Spring).
// ─────────────────────────────────────────────────────────────────────────────
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.cloud:spring-cloud-starter-consul-discovery")
    implementation("org.springframework.cloud:spring-cloud-starter-consul-config")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-zipkin")

    // Migraciones de esquema con Liquibase
    implementation("org.liquibase:liquibase-core")

    // Driver H2 R2DBC para desarrollo (in-memory)
    runtimeOnly("io.r2dbc:r2dbc-h2")
    // Driver H2 JDBC: requerido por Liquibase (usa JDBC, no R2DBC)
    runtimeOnly("com.h2database:h2")

    // Driver MySQL para producción
    runtimeOnly("io.asyncer:r2dbc-mysql:1.4.0")
}
