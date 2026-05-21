package com.cursosdedesarrollo.serviciopedidos.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder(ObservationRegistry observationRegistry) {
        // observationRegistry conecta el WebClient con Micrometer Tracing:
        // cada petición saliente hereda el span activo del contexto reactivo y genera un span hijo,
        // propagando el traceId al servicio destino mediante cabeceras b3/traceparent.
        return WebClient.builder().observationRegistry(observationRegistry);
    }
}
