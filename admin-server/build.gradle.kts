// ─────────────────────────────────────────────────────────────────────────────
// Stack: Spring Boot 4.0.5 · Spring Cloud 2025.1.1 (Oakwood) · Java 25
// Las versiones de las dependencias Spring las gestiona el BOM del build.gradle.kts raíz.
// spring-boot-admin-starter-server: versión fijada explícitamente (no está en el BOM de Spring).
// ─────────────────────────────────────────────────────────────────────────────
dependencies {
    implementation("de.codecentric:spring-boot-admin-starter-server:4.0.4")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Fuerza versiones más recientes de dependencias transitivas de spring-boot-admin
    // con CVEs conocidas. Eliminar cuando spring-boot-admin publique 4.0.5+.
    constraints {
        implementation("org.bouncycastle:bcprov-jdk18on:1.84") {
            because("CVE-2026-40971: actualización de seguridad de BouncyCastle")
        }
        // Thymeleaf está pinado en 3.1.3.RELEASE por el BOM de Spring Boot 4.0.5;
        // no se puede subir via constraints sin romper el BOM. Revisar cuando SBA publique 4.0.5+.
    }
}
