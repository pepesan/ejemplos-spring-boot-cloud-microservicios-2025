package com.cursosdedesarrollo.configclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Microservicio cliente del Config Server.
 *
 * <p>Al arrancar, importa su configuración desde el servidor centralizado
 * ({@code http://localhost:8888}) usando el nombre de aplicación {@code config-client}.
 * El Config Server resuelve qué ficheros servir en este orden de prioridad:
 * <ol>
 *   <li>{@code config-client-<perfil>.yml} — propiedades específicas del perfil activo</li>
 *   <li>{@code config-client.yml} — propiedades base del servicio (todos los perfiles)</li>
 *   <li>{@code application.yml} — propiedades compartidas por todos los servicios</li>
 * </ol>
 *
 * <p>El perfil activo se selecciona al arrancar:
 * <pre>
 *   # Desarrollo (por defecto si no se indica ninguno)
 *   ./gradlew :config-client:bootRun
 *
 *   # Perfil explícito
 *   ./gradlew :config-client:bootRun --args='--spring.profiles.active=desarrollo'
 *   ./gradlew :config-client:bootRun --args='--spring.profiles.active=produccion'
 * </pre>
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class ConfigClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigClientApplication.class, args);
    }
}
