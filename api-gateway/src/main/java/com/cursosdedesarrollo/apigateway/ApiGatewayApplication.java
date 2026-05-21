package com.cursosdedesarrollo.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada único al ecosistema de microservicios.
 *
 * <p>Spring Cloud Gateway es reactivo por naturaleza (construido sobre WebFlux y Netty),
 * por lo que actúa como proxy no bloqueante entre los clientes externos y los servicios
 * internos registrados en Eureka.
 *
 * <p>Las rutas se definen en {@code config-repo/api-gateway/api-gateway.yml} y se cargan
 * desde el Config Server al arrancar. El esquema {@code lb://} en la URI de cada ruta
 * delega la resolución de la instancia destino al LoadBalancer de Spring Cloud, que
 * consulta el registro de Eureka para obtener las instancias disponibles.
 *
 * <p>Flujo de una petición:
 * <pre>
 *   Cliente → GET /eureka-client/hola
 *       → Gateway aplica predicado Path=/eureka-client/**  ✓
 *       → Filtro StripPrefix=1 elimina /eureka-client del path
 *       → LoadBalancer resuelve lb://eureka-client a una instancia real
 *       → Petición reenviada a http://&lt;ip&gt;:8081/hola
 * </pre>
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
