package com.cursosdedesarrollo.consulgateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifica que el contexto arranca correctamente sin Consul ni servicios destino.
 *
 * Se deshabilitan Consul y las rutas para que el LoadBalancer no intente
 * resolver instancias inexistentes durante el arranque del contexto de test.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.config.import=",
        "spring.cloud.gateway.server.webflux.routes="
})
class ConsulGatewayApplicationTest {

    @Test
    void contextLoads() {
    }
}
