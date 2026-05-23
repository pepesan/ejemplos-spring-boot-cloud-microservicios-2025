package com.cursosdedesarrollo.serviciopedidos.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
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

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        // @LoadBalanced añade LoadBalancerInterceptor: resuelve http://servicio-X en la IP:puerto
        // real consultando Eureka antes de cada petición HTTP.
        // NOTA: RestTemplate es seguro con @LoadBalanced porque Spring Boot no auto-configura
        // un bean RestTemplate, por lo que no hay conflicto de @ConditionalOnMissingBean.
        return new RestTemplate();
    }
}
