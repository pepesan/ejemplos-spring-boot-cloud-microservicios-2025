springBoot {
    buildInfo()
}

dependencies {
    // API reactiva
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Persistencia reactiva con R2DBC + H2 en memoria
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("io.r2dbc:r2dbc-h2")

    // Registro y descubrimiento — también habilita @LoadBalanced en WebClient
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")

    // Configuración centralizada desde el Config Server
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    // Publicación de eventos en Kafka con StreamBridge (modelo imperativo)
    implementation("org.springframework.cloud:spring-cloud-starter-stream-kafka")

    // Circuit Breaker reactivo con Resilience4j
    // Protege la llamada síncrona a servicio-productos sin bloquear el hilo reactivo
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")

    // Monitorización y health checks
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-zipkin")

    // Binder en memoria para tests: sustituye Kafka sin necesitar broker real
    testImplementation("org.springframework.cloud:spring-cloud-stream-test-binder")
    // WebTestClient para tests de integración reactivos
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    // StepVerifier para verificar pipelines reactivos en tests
    testImplementation("io.projectreactor:reactor-test")
}
