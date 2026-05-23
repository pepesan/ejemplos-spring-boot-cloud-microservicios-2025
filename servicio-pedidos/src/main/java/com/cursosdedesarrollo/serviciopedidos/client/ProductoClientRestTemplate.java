package com.cursosdedesarrollo.serviciopedidos.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Cliente bloqueante para {@code servicio-productos} usando {@link RestTemplate}.
 *
 * <p>{@link RestTemplate} es el cliente HTTP bloqueante clásico de Spring, disponible
 * desde Spring 3.0. Aunque desde Spring 6 se recomienda usar {@link org.springframework.web.client.RestClient},
 * {@code RestTemplate} sigue en mantenimiento y es ampliamente utilizado en proyectos existentes.
 *
 * <p>El bean {@link RestTemplate} se declara con {@code @LoadBalanced} en {@code WebClientConfig},
 * lo que permite usar {@code http://servicio-productos} como host: Spring Cloud LoadBalancer
 * intercepta la petición y resuelve la instancia real consultando Eureka.
 *
 * <p>La llamada está protegida por un {@link CircuitBreaker} de Resilience4j (versión bloqueante).
 * Si el circuito está abierto o la llamada falla, el fallback devuelve {@link Optional#empty()}.
 *
 * <p><strong>Uso en contexto reactivo:</strong> este cliente bloquea el hilo. Siempre hay que
 * invocarlo desde {@code Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())} para
 * no bloquear los hilos del event loop de Netty.
 */
@Slf4j
@Component
public class ProductoClientRestTemplate {

    private final RestTemplate restTemplate;
    private final CircuitBreaker circuitBreaker;

    @SuppressWarnings("rawtypes")
    public ProductoClientRestTemplate(RestTemplate restTemplate,
                                      CircuitBreakerFactory circuitBreakerFactory) {
        this.restTemplate = restTemplate;
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
                        restTemplate.getForObject(
                                "http://servicio-productos/productos/{id}",
                                ProductoInfo.class,
                                id
                        )
                ),
                throwable -> {
                    log.warn("Circuit breaker abierto (RestTemplate) para producto id={} — fallback vacío: {}",
                            id, throwable.getMessage());
                    return Optional.empty();
                }
        );
    }
}
