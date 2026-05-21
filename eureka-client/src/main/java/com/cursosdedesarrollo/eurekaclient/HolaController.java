package com.cursosdedesarrollo.eurekaclient;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Controlador reactivo de ejemplo que ilustra el uso de {@link DiscoveryClient}
 * para consultar el registro de Eureka en tiempo de ejecución.
 *
 * <p>{@link DiscoveryClient} es la abstracción genérica de Spring Cloud para el
 * descubrimiento de servicios; funciona con Eureka, Consul, Zookeeper, etc. sin
 * cambiar el código de negocio.
 *
 * <p>Nota: {@code DiscoveryClient} realiza llamadas bloqueantes al servidor Eureka.
 * Se envuelven en {@code Flux.fromIterable} para respetar la API reactiva del
 * controlador, pero la llamada real no es no-bloqueante. Para un uso reactivo puro
 * se debería emplear {@code ReactiveDiscoveryClient} (disponible con el starter de
 * Eureka a partir de Spring Cloud 2020.x).
 */
@RestController
@RequiredArgsConstructor
public class HolaController {

    private final DiscoveryClient discoveryClient;

    /**
     * Endpoint raíz del servicio.
     *
     * @return nombre y estado del servicio como {@code Mono<String>}
     */
    @GetMapping("/")
    public Mono<String> raiz() {
        return Mono.just("eureka-client activo");
    }

    /**
     * Endpoint de comprobación básica de que el servicio está activo.
     *
     * @return saludo simple como {@code Mono<String>}
     */
    @GetMapping("/hola")
    public Mono<String> hola() {
        return Mono.just("Hola desde eureka-client!");
    }

    /**
     * Devuelve los nombres de todos los servicios registrados en el servidor Eureka.
     *
     * <p>Útil para verificar qué microservicios están activos en el ecosistema.
     *
     * @return {@code Flux} con los nombres de los servicios (p. ej. {@code eureka-client}, {@code eureka-server})
     */
    @GetMapping("/servicios")
    public Flux<String> serviciosRegistrados() {
        return Flux.fromIterable(discoveryClient.getServices());
    }

    /**
     * Devuelve las instancias registradas del propio servicio {@code eureka-client}.
     *
     * <p>Muestra la relación {@code serviceId -> URI} de cada instancia, permitiendo
     * observar el balanceo en caso de que haya varias instancias levantadas.
     *
     * @return {@code Flux} con cadenas del tipo {@code EUREKA-CLIENT -> http://192.168.1.x:8081}
     */
    @GetMapping("/instancias")
    public Flux<String> instancias() {
        List<ServiceInstance> instancias = discoveryClient.getInstances("eureka-client");
        return Flux.fromIterable(instancias)
                .map(i -> i.getServiceId() + " -> " + i.getUri());
    }
}
