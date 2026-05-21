package com.cursosdedesarrollo.configclient;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Map;

/**
 * Controlador reactivo que expone las propiedades recibidas del Config Server.
 *
 * <p>Permite verificar en tiempo de ejecución qué configuración está activa
 * y desde qué perfil proviene, lo que resulta útil para demostrar la diferencia
 * entre los perfiles {@code desarrollo} y {@code produccion}.
 */
@RestController
@RequiredArgsConstructor
public class ConfigController {

    private final AppProperties appProperties;
    private final Environment environment;

    /**
     * Devuelve las propiedades activas recibidas del Config Server.
     *
     * @return mapa con el entorno, mensaje, límite de peticiones y perfiles activos
     */
    @GetMapping("/config")
    public Mono<Map<String, Object>> config() {
        return Mono.just(Map.of(
                "entorno", appProperties.getEntorno(),
                "mensaje", appProperties.getMensaje(),
                "limitePeticiones", appProperties.getLimitePeticiones(),
                "perfilesActivos", Arrays.asList(environment.getActiveProfiles())
        ));
    }
}
