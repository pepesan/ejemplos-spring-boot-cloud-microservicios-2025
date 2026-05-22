package com.cursosdedesarrollo.consulproductos.service;

import com.cursosdedesarrollo.consulproductos.domain.Producto;
import com.cursosdedesarrollo.consulproductos.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository repository;

    public Flux<Producto> findAll() {
        return repository.findAll();
    }

    public Mono<Producto> findById(Long id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado: " + id)));
    }

    public Mono<Producto> save(Producto producto) {
        return repository.save(producto);
    }

    public Mono<Producto> update(Long id, Producto producto) {
        return findById(id).flatMap(existing -> {
            existing.setNombre(producto.getNombre());
            existing.setDescripcion(producto.getDescripcion());
            existing.setPrecio(producto.getPrecio());
            existing.setStock(producto.getStock());
            return repository.save(existing);
        });
    }

    public Mono<Void> deleteById(Long id) {
        return findById(id).flatMap(repository::delete);
    }

    public Mono<Producto> decrementarStock(Long id, Integer cantidad) {
        return findById(id).flatMap(p -> {
            p.setStock(Math.max(0, p.getStock() - cantidad));
            return repository.save(p);
        });
    }
}
