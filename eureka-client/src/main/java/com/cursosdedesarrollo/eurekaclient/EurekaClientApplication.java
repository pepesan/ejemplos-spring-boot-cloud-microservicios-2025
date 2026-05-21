package com.cursosdedesarrollo.eurekaclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Microservicio de ejemplo que demuestra el registro automático en Eureka.
 *
 * <p>Al arrancar, se registra en el servidor Eureka configurado en
 * {@code eureka.client.service-url.defaultZone} con el nombre de aplicación
 * definido en {@code spring.application.name}.
 *
 * <p>La anotación {@code @EnableDiscoveryClient} no es necesaria a partir de
 * Spring Cloud 3.x: basta con tener la dependencia
 * {@code spring-cloud-starter-netflix-eureka-client} en el classpath.
 *
 * <p>Expone los endpoints de ejemplo en el puerto {@code 8081}:
 * <ul>
 *   <li>{@code GET /hola} — saludo simple</li>
 *   <li>{@code GET /servicios} — lista de servicios registrados en Eureka</li>
 *   <li>{@code GET /instancias} — instancias propias visibles desde el registro</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class EurekaClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaClientApplication.class, args);
    }
}
