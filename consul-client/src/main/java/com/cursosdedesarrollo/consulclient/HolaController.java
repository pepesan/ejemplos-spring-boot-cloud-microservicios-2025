package com.cursosdedesarrollo.consulclient;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HolaController {

    private final ReactiveDiscoveryClient discoveryClient;
    private final ConsulConfigProperties config;
    private final DatabaseProperties dbProps;

    @GetMapping("/")
    public Mono<String> raiz() {
        return Mono.just("consul-client activo");
    }

    @GetMapping("/hola")
    public Mono<String> hola() {
        return Mono.just("Hola desde consul-client!");
    }

    @GetMapping("/servicios")
    public Mono<List<String>> serviciosRegistrados() {
        return discoveryClient.getServices().collectList();
    }

    @GetMapping("/instancias/{serviceId}")
    public Mono<List<String>> instancias(@PathVariable String serviceId) {
        return discoveryClient.getInstances(serviceId)
                .map(i -> i.getServiceId() + " -> " + i.getUri())
                .collectList();
    }

    @GetMapping("/instancias")
    public Mono<List<String>> instanciasPropio() {
        return instancias("consul-client");
    }

    /**
     * Devuelve la configuración activa leída desde Consul KV (config/consul-client/data).
     * Si el KV no tiene valor, se devuelven los defaults definidos en la clase de propiedades.
     * Al modificar el KV en Consul, el watcher refresca este bean automáticamente.
     */
    @GetMapping("/config")
    public Mono<Map<String, Object>> configuracion() {
        return Mono.just(Map.of(
                "mensaje", config.getMensaje(),
                "limite",  config.getLimite(),
                "entorno", config.getEntorno()
        ));
    }

    /**
     * Devuelve la configuración de base de datos activa para el perfil en curso.
     * La password se enmascara: nunca se expone el valor real.
     *
     * Ruta KV por perfil:
     *   sin perfil  → config/consul-client/data
     *   desarrollo  → config/consul-client,desarrollo/data
     *   produccion  → config/consul-client,produccion/data
     */
    @GetMapping("/db-config")
    public Mono<Map<String, Object>> dbConfig() {
        return Mono.just(Map.of(
                "url",      dbProps.getUrl(),
                "username", dbProps.getUsername(),
                "password", dbProps.getPassword().isBlank() ? "(no configurado)" : "***",
                "poolSize", dbProps.getPoolSize()
        ));
    }
}
