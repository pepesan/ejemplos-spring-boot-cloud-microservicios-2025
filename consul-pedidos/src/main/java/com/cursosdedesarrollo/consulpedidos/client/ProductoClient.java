package com.cursosdedesarrollo.consulpedidos.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Cliente reactivo para consul-productos.
 *
 * lb://consul-productos activa el LoadBalancer de Spring Cloud, que resuelve
 * la instancia real consultando el catálogo de Consul en cada petición.
 * Si el circuit breaker está abierto o el servicio no responde, el fallback
 * devuelve Mono.empty() y el pedido se crea sin total calculado.
 */
@Slf4j
@Component
public class ProductoClient {

    private final WebClient webClient;
    private final ReactiveCircuitBreaker circuitBreaker;

    @SuppressWarnings("rawtypes")
    public ProductoClient(WebClient.Builder webClientBuilder,
                          ReactiveCircuitBreakerFactory circuitBreakerFactory) {
        this.webClient = webClientBuilder.baseUrl("http://consul-productos").build();
        this.circuitBreaker = circuitBreakerFactory.create("consul-productos-cb");
    }

    public Mono<ProductoInfo> findById(Long id) {
        Mono<ProductoInfo> llamada = webClient.get()
                .uri("/productos/{id}", id)
                .retrieve()
                .bodyToMono(ProductoInfo.class)
                .doOnError(e -> log.warn("Error consultando producto id={}: {}", id, e.getMessage()));

        return circuitBreaker.run(llamada, ex -> {
            log.warn("Circuit breaker abierto (consul-productos-cb) — pedido sin total");
            return Mono.empty();
        });
    }
}
