package com.cursosdedesarrollo.eurekaserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifica que el contexto de Spring arranca correctamente con la configuración
 * del servidor Eureka. No requiere ningún cliente registrado para pasar.
 */
@SpringBootTest
class EurekaServerApplicationTest {

    @Test
    void contextLoads() {
    }
}
