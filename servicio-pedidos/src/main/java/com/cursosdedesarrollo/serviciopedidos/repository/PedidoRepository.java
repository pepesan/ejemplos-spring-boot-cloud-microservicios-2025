package com.cursosdedesarrollo.serviciopedidos.repository;

import com.cursosdedesarrollo.serviciopedidos.domain.Pedido;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * Repositorio reactivo para la entidad {@link Pedido}.
 *
 * <p>Extiende {@link ReactiveCrudRepository} para obtener las operaciones CRUD
 * estándar como {@code Mono}/{@code Flux}.
 */
public interface PedidoRepository extends ReactiveCrudRepository<Pedido, Long> {

    /** Devuelve todos los pedidos de un producto concreto. */
    Flux<Pedido> findByProductoId(Long productoId);
}
