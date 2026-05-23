package com.cursosdedesarrollo.serviciopedidos.controller;

import com.cursosdedesarrollo.serviciopedidos.client.ProductoInfo;
import com.cursosdedesarrollo.serviciopedidos.domain.Pedido;
import com.cursosdedesarrollo.serviciopedidos.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST reactivo para el CRUD de pedidos.
 *
 * <p>Todos los endpoints devuelven {@link Mono} o {@link Flux}, lo que permite
 * a Netty gestionar las peticiones de forma no bloqueante.
 *
 * <p>El endpoint {@code POST /pedidos} orquesta la creación del pedido, la consulta
 * del producto (con circuit breaker) y la publicación del evento Kafka.
 */
@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService service;

    /** Devuelve todos los pedidos. */
    @GetMapping
    public Flux<Pedido> findAll() {
        return service.findAll();
    }

    /** Devuelve un pedido por id, o 404 si no existe. */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Pedido>> findById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /** Devuelve todos los pedidos de un producto concreto. */
    @GetMapping("/producto/{productoId}")
    public Flux<Pedido> findByProductoId(@PathVariable Long productoId) {
        return service.findByProductoId(productoId);
    }

    /**
     * Crea un pedido en <strong>modo estricto</strong>.
     *
     * <p>Si {@code servicio-productos} no responde o el circuit breaker está abierto,
     * devuelve {@code 503 Service Unavailable} y el pedido NO se persiste.
     * Garantiza que todos los pedidos almacenados tienen {@code total} calculado.
     *
     * <p>Úsalo cuando la integridad del dato sea prioritaria sobre la disponibilidad.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Pedido> crear(@RequestBody Pedido pedido) {
        return service.crear(pedido);
    }

    /**
     * Crea un pedido en <strong>modo resiliente</strong> (degradación graceful).
     *
     * <p>Si {@code servicio-productos} no responde o el circuit breaker está abierto,
     * el pedido se persiste igualmente con {@code total = null} y se publica el evento
     * Kafka. Cuando el servicio se recupere, el stock se decrementará por consistencia eventual.
     *
     * <p>Úsalo cuando la disponibilidad sea prioritaria sobre la integridad inmediata del dato.
     */
    @PostMapping("/resiliente")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Pedido> crearResiliente(@RequestBody Pedido pedido) {
        return service.crearResiliente(pedido);
    }

    /**
     * Actualiza el estado de un pedido existente (PENDIENTE, CONFIRMADO, CANCELADO).
     * Devuelve 404 si el pedido no existe.
     */
    @PatchMapping("/{id}/estado")
    public Mono<ResponseEntity<Pedido>> actualizarEstado(
            @PathVariable Long id,
            @RequestParam String estado) {
        return service.actualizarEstado(id, estado)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /** Elimina un pedido por id, o devuelve 404 si no existe. */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteById(@PathVariable Long id) {
        ResponseEntity<Void> notFound = ResponseEntity.notFound().build();
        return service.findById(id)
                .<ResponseEntity<Void>>flatMap(p -> service.deleteById(id)
                        .then(Mono.just(ResponseEntity.<Void>noContent().build())))
                .switchIfEmpty(Mono.just(notFound));
    }

    /**
     * Consulta un producto en {@code servicio-productos} desde este servicio usando
     * {@link org.springframework.web.client.RestClient} (bloqueante, Spring 6+).
     *
     * <p>Demuestra cómo integrar una llamada HTTP bloqueante en un pipeline reactivo:
     * la llamada se delega a {@code Schedulers.boundedElastic()} para no bloquear Netty.
     *
     * @param id id del producto a consultar
     * @return información del producto (200) o 404 si no existe o el servicio no responde
     */
    @GetMapping("/demo/producto/{id}/restclient")
    public Mono<ResponseEntity<ProductoInfo>> findProductoRestClient(@PathVariable Long id) {
        return service.findProductoByIdRestClient(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Consulta un producto en {@code servicio-productos} desde este servicio usando
     * {@link org.springframework.web.client.RestTemplate} (bloqueante, clásico Spring).
     *
     * <p>Mismo patrón de integración reactiva que el endpoint {@code restclient},
     * pero ilustra la API de {@code RestTemplate}, más antigua y aún ampliamente usada.
     *
     * @param id id del producto a consultar
     * @return información del producto (200) o 404 si no existe o el servicio no responde
     */
    @GetMapping("/demo/producto/{id}/resttemplate")
    public Mono<ResponseEntity<ProductoInfo>> findProductoRestTemplate(@PathVariable Long id) {
        return service.findProductoByIdRestTemplate(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
