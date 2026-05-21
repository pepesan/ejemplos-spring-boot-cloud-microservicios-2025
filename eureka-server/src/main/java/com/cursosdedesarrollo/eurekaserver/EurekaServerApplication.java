package com.cursosdedesarrollo.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Servidor de registro y descubrimiento basado en Netflix Eureka.
 *
 * <p>Actúa como registro central donde los microservicios clientes se registran
 * al arrancar y desde el que otros servicios consultan las instancias disponibles.
 *
 * <p>Dashboard accesible en {@code http://localhost:8761} una vez arrancado.
 *
 * <p>Para que un cliente se registre debe incluir la dependencia
 * {@code spring-cloud-starter-netflix-eureka-client} y configurar:
 * <pre>
 * eureka:
 *   client:
 *     service-url:
 *       defaultZone: http://localhost:8761/eureka/
 * </pre>
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
