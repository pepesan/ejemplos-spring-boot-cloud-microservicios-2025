package com.cursosdedesarrollo.consulproductos.controller;

import com.cursosdedesarrollo.consulproductos.domain.Producto;
import com.cursosdedesarrollo.consulproductos.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService service;

    @GetMapping
    public Flux<Producto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Mono<Producto> findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Producto> create(@RequestBody Producto producto) {
        producto.setId(null);
        return service.save(producto);
    }

    @PutMapping("/{id}")
    public Mono<Producto> update(@PathVariable Long id, @RequestBody Producto producto) {
        return service.update(id, producto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id) {
        return service.deleteById(id);
    }

    @PatchMapping("/{id}/stock")
    public Mono<Producto> decrementarStock(@PathVariable Long id,
                                           @RequestParam Integer cantidad) {
        return service.decrementarStock(id, cantidad);
    }
}
