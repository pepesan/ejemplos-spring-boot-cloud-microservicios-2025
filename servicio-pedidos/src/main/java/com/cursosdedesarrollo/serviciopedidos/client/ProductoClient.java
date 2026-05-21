package com.cursosdedesarrollo.serviciopedidos.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Cliente reactivo para {@code servicio-productos}.
 *
 * <p>Usa un {@link WebClient} con load balancing (resuelve {@code servicio-productos}
 * consultando el registro de Eureka) y un {@link ReactiveCircuitBreaker} de
 * Resilience4j para proteger el flujo cuando el servicio destino no está disponible.
 *
 * <p>Si el circuit breaker está abierto o la llamada falla, el fallback devuelve
 * {@link Mono#empty()}, lo que permite al servicio de pedidos seguir operando sin
 * la información del producto (consistencia eventual vía Kafka).
 */
@Slf4j
@Component
public class ProductoClient {

    private final WebClient webClient;
    private final ReactiveCircuitBreaker circuitBreaker;

    @SuppressWarnings("rawtypes")
    public ProductoClient(WebClient.Builder webClientBuilder,
                          ReactiveCircuitBreakerFactory circuitBreakerFactory) {
        // El nombre lb://servicio-productos activa el LoadBalancer de Spring Cloud,
        // que resuelve la instancia real consultando Eureka en cada petición.
        this.webClient = webClientBuilder.baseUrl("http://servicio-productos").build();
        // El id "producto-cb" identifica la configuración del circuit breaker en Resilience4j.
        this.circuitBreaker = circuitBreakerFactory.create("producto-cb");
    }

    /**
     * Obtiene la información de un producto por su id.
     *
     * <p>Si el circuit breaker está abierto o {@code servicio-productos} devuelve error,
     * el fallback emite {@link Mono#empty()} en lugar de propagar la excepción.
     *
     * @param id identificador del producto
     * @return información del producto, o vacío si no existe o el servicio no está disponible
     */
    public Mono<ProductoInfo> findById(Long id) {
        Mono<ProductoInfo> llamada = webClient.get()
                .uri("/productos/{id}", id)
                .retrieve()
                .bodyToMono(ProductoInfo.class)
                .doOnError(e -> log.warn("Error al consultar producto id={}: {}", id, e.getMessage()));

        return circuitBreaker.run(llamada, throwable -> {
            log.warn("Circuit breaker abierto para producto-cb — usando fallback vacío");
            return Mono.empty();
        });
    }
}
