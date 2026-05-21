package com.cursosdedesarrollo.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Servidor centralizado de configuración basado en Spring Cloud Config Server.
 *
 * <p>Lee los ficheros de configuración del repositorio Git local {@code config-repo/}
 * en la raíz del proyecto y los sirve a los microservicios clientes a través de la
 * API REST:
 * <pre>
 *   GET /{application}/{profile}
 *   GET /{application}/{profile}/{label}
 * </pre>
 *
 * <p>Los clientes consumen la configuración añadiendo en su {@code application.yml}:
 * <pre>
 * spring:
 *   config:
 *     import: "optional:configserver:http://localhost:8888"
 * </pre>
 * y la dependencia {@code spring-cloud-starter-config}.
 *
 * <p>Se registra en Eureka para que los clientes puedan localizarlo por nombre
 * ({@code config-server}) en lugar de por URL fija, combinándolo con
 * {@code spring.cloud.config.discovery.enabled=true} en los clientes.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
