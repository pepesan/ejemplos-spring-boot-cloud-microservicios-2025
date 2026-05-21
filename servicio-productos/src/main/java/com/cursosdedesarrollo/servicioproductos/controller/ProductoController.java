package com.cursosdedesarrollo.servicioproductos.controller;

import com.cursosdedesarrollo.servicioproductos.domain.Producto;
import com.cursosdedesarrollo.servicioproductos.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST reactivo para el CRUD de productos.
 *
 * <p>Todos los endpoints devuelven {@link Mono} o {@link Flux}, lo que permite
 * a Netty gestionar las peticiones de forma no bloqueante.
 */
@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService service;

    /** Devuelve todos los productos como stream reactivo. */
    @GetMapping
    public Flux<Producto> findAll() {
        return service.findAll();
    }

    /** Devuelve un producto por id, o 404 si no existe. */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Producto>> findById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /** Crea un producto nuevo. El id es generado por la base de datos. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Producto> create(@RequestBody Producto producto) {
        producto.setId(null);
        return service.save(producto);
    }

    /** Actualiza un producto existente, o devuelve 404 si no existe. */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Producto>> update(@PathVariable Long id, @RequestBody Producto producto) {
        return service.findById(id)
                .flatMap(existing -> {
                    producto.setId(id);
                    return service.save(producto);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /** Elimina un producto por id, o devuelve 404 si no existe. */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteById(@PathVariable Long id) {
        return service.findById(id)
                .<ResponseEntity<Void>>flatMap(p -> service.deleteById(id)
                        .then(Mono.just(ResponseEntity.<Void>noContent().build())))
                .switchIfEmpty(Mono.just(ResponseEntity.<Void>notFound().build()));
    }
}
