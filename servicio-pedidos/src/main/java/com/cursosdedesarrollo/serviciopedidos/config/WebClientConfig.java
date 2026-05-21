package com.cursosdedesarrollo.serviciopedidos.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuración del {@link WebClient} con load balancing.
 *
 * <p>La anotación {@link LoadBalanced} instruye a Spring Cloud para interceptar
 * las peticiones del cliente e integrarlas con el LoadBalancer, que consulta
 * Eureka para resolver nombres de servicio como {@code http://servicio-productos}
 * en instancias reales (IP:puerto).
 */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
