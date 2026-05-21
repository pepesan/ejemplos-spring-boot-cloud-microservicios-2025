package com.cursosdedesarrollo.servicioproductos.service;

import com.cursosdedesarrollo.servicioproductos.domain.Producto;
import com.cursosdedesarrollo.servicioproductos.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Lógica de negocio para la gestión de productos.
 *
 * <p>Todos los métodos son no bloqueantes y devuelven tipos reactivos
 * ({@link Mono}/{@link Flux}) para integrarse con el pipeline reactivo de WebFlux.
 */
@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository repository;

    public Flux<Producto> findAll() {
        return repository.findAll();
    }

    public Mono<Producto> findById(Long id) {
        return repository.findById(id);
    }

    public Mono<Producto> save(Producto producto) {
        return repository.save(producto);
    }

    public Mono<Void> deleteById(Long id) {
        return repository.deleteById(id);
    }

    /**
     * Decrementa el stock de un producto tras recibir un evento {@code PedidoCreado}.
     *
     * <p>El stock nunca baja de 0 para evitar valores negativos en caso de
     * condiciones de carrera o eventos duplicados.
     *
     * @param productoId id del producto cuyo stock se reduce
     * @param cantidad   unidades pedidas
     * @return el producto actualizado, o {@link Mono#empty()} si no existe
     */
    public Mono<Producto> decrementarStock(Long productoId, Integer cantidad) {
        return repository.findById(productoId)
                .flatMap(p -> {
                    p.setStock(Math.max(0, p.getStock() - cantidad));
                    return repository.save(p);
                });
    }
}
