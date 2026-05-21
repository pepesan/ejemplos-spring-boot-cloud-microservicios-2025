package com.cursosdedesarrollo.serviciopedidos.controller;

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
     * Crea un pedido nuevo.
     *
     * <p>Internamente: consulta el producto con circuit breaker, guarda el pedido
     * con estado PENDIENTE y publica el evento {@code PedidoCreado} en Kafka.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Pedido> crear(@RequestBody Pedido pedido) {
        return service.crear(pedido);
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
}
