package com.cursosdedesarrollo.configclient;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifica que el contexto arranca correctamente sin Config Server ni Eureka disponibles.
 *
 * <p>Se inyectan directamente las propiedades {@code app.*} para que
 * {@link AppProperties} se inicialice sin necesitar el servidor de configuración.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.config.import=",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "app.mensaje=test",
        "app.entorno=test",
        "app.limite-peticiones=1"
})
class ConfigClientApplicationTest {

    @Test
    void contextLoads() {
    }
}
