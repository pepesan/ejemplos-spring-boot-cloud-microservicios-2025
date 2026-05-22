dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.cloud:spring-cloud-starter-consul-discovery")
    implementation("org.springframework.cloud:spring-cloud-starter-consul-config")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-zipkin")

    // Driver H2 para desarrollo (in-memory)
    runtimeOnly("io.r2dbc:r2dbc-h2")

    // Driver MySQL para producción
    runtimeOnly("io.asyncer:r2dbc-mysql")
}
