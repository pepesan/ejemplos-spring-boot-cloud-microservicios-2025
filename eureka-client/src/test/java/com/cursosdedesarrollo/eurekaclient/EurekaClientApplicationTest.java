package com.cursosdedesarrollo.eurekaclient;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifica que el contexto de Spring arranca correctamente sin necesitar
 * un servidor Eureka disponible.
 *
 * <p>Se desactiva el cliente Eureka ({@code eureka.client.enabled=false}) y el
 * descubrimiento de servicios ({@code spring.cloud.discovery.enabled=false}) para
 * que el test sea autocontenido y no falle en entornos de CI sin infraestructura.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class EurekaClientApplicationTest {

    @Test
    void contextLoads() {
    }
}
