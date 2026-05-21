dependencies {
    // En Spring Cloud Gateway 5.x (Oakwood) el starter reactivo fue renombrado.
    // spring-cloud-starter-gateway-server-webflux → gateway reactivo (WebFlux/Netty)
    // spring-cloud-starter-gateway-server-webmvc  → gateway MVC (Tomcat, no reactivo)
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
