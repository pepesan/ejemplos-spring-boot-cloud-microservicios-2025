package com.cursosdedesarrollo.consulgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway del stack Consul.
 *
 * <p>Actúa como punto de entrada único para todos los servicios registrados en Consul.
 * Las rutas se cargan desde el KV store de Consul al arrancar
 * (clave {@code config/consul-gateway/data}), lo que permite añadir o modificar
 * rutas sin recompilar ni reiniciar el gateway.
 *
 * <p>El esquema {@code lb://} en la URI de cada ruta delega la resolución de la
 * instancia destino al LoadBalancer de Spring Cloud, que consulta el catálogo de
 * Consul para obtener las instancias disponibles y sanas.
 *
 * <p>Flujo de una petición:
 * <pre>
 *   Cliente → GET /consul-client/hola
 *       → Gateway aplica predicado Path=/consul-client/**  ✓
 *       → Filtro StripPrefix=1 elimina /consul-client del path
 *       → LoadBalancer resuelve lb://consul-client a una instancia sana
 *       → Petición reenviada a http://&lt;ip&gt;:8085/hola
 * </pre>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ConsulGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsulGatewayApplication.class, args);
    }
}
