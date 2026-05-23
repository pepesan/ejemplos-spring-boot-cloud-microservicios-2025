package com.cursosdedesarrollo.serviciopedidos.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Cliente bloqueante para {@code servicio-productos} usando {@link RestClient} (Spring 6+).
 *
 * <p>{@link RestClient} es el sustituto moderno de {@link org.springframework.web.client.RestTemplate}.
 * Ofrece una API fluent similar a {@link org.springframework.web.reactive.function.client.WebClient}
 * pero ejecuta las peticiones de forma síncrona/bloqueante.
 *
 * <p>El load balancing se activa añadiendo {@link LoadBalancerInterceptor} directamente al builder,
 * en lugar de usar el mecanismo {@code @LoadBalanced}. Esto evita el conflicto que surge al declarar
 * un bean {@code @LoadBalanced RestClient.Builder}: Spring Boot no crearía su bean auto-configurado
 * de {@code RestClient.Builder} (por {@code @ConditionalOnMissingBean}), y el cliente interno de
 * Eureka terminaría resolviendo {@code localhost} como nombre de servicio a través del load balancer,
 * lo que falla con {@code No instances available for service: localhost}.
 *
 * <p>La llamada está protegida por un {@link CircuitBreaker} de Resilience4j (versión bloqueante,
 * a diferencia del {@link org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker}
 * que usa {@link ProductoClient}). Si el circuito está abierto o la llamada falla, el fallback
 * devuelve {@link Optional#empty()}.
 *
 * <p><strong>Uso en contexto reactivo:</strong> este cliente BLOQUEA el hilo que lo llama.
 * Siempre hay que envolverlo con
 * {@code Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())}.
 */
@Slf4j
@Component
public class ProductoClientRestClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    @SuppressWarnings("rawtypes")
    public ProductoClientRestClient(LoadBalancerClient loadBalancerClient,
                                    RestClient.Builder restClientBuilder,
                                    CircuitBreakerFactory circuitBreakerFactory) {
        // LoadBalancerInterceptor resuelve "servicio-productos" en IP:puerto real via Eureka,
        // sin registrar un bean @LoadBalanced RestClient.Builder que interferiría con Eureka client.
        this.restClient = restClientBuilder
                .baseUrl("http://servicio-productos")
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .build();
        // CircuitBreakerFactory (no reactiva) es la contraparte bloqueante de
        // ReactiveCircuitBreakerFactory. Comparte el id y la configuración con los otros clientes.
        this.circuitBreaker = circuitBreakerFactory.create("producto-cb");
    }

    /**
     * Obtiene la información de un producto por su id (llamada bloqueante + circuit breaker).
     *
     * <p>Si el circuito está abierto o {@code servicio-productos} devuelve error,
     * el fallback emite {@link Optional#empty()} en lugar de propagar la excepción.
     *
     * @param id identificador del producto
     * @return información del producto, o vacío si no existe o el circuit breaker está abierto
     */
    public Optional<ProductoInfo> findById(Long id) {
        return circuitBreaker.run(
                () -> Optional.ofNullable(
                        restClient.get()
                                .uri("/productos/{id}", id)
                                .retrieve()
                                .body(ProductoInfo.class)
                ),
                throwable -> {
                    log.warn("Circuit breaker abierto (RestClient) para producto id={} — fallback vacío: {}",
                            id, throwable.getMessage());
                    return Optional.empty();
                }
        );
    }
}
