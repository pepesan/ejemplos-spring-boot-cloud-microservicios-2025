package com.cursosdedesarrollo.consulpedidos.config;

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
        // ObservationRegistry propaga el traceId activo al WebClient saliente,
        // conectando el span de consul-pedidos con el de consul-productos en Zipkin.
        return WebClient.builder().observationRegistry(observationRegistry);
    }
}
