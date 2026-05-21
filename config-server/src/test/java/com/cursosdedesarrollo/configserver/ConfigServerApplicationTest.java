package com.cursosdedesarrollo.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifica que el contexto de Spring arranca correctamente sin necesitar
 * un servidor Eureka disponible.
 *
 * <p>El perfil {@code native} ya está activo por defecto en {@code application.yml},
 * por lo que el Config Server lee los YAML desde el sistema de ficheros sin necesidad
 * de un repositorio Git. Solo se desactiva Eureka para que el test sea autocontenido.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class ConfigServerApplicationTest {

    @Test
    void contextLoads() {
    }
}
