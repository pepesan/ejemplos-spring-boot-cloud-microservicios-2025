package com.cursosdedesarrollo.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifica que el contexto arranca correctamente sin Config Server, Eureka
 * ni servicios destino disponibles.
 *
 * <p>Se desactivan las rutas configuradas ({@code spring.cloud.gateway.routes=})
 * para evitar que el LoadBalancer intente resolver instancias inexistentes durante
 * el arranque del contexto de test.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.config.import=",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.gateway.routes="
})
class ApiGatewayApplicationTest {

    @Test
    void contextLoads() {
    }
}
