package com.cursosdedesarrollo.servicioproductos.repository;

import com.cursosdedesarrollo.servicioproductos.domain.Producto;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * Repositorio reactivo para la entidad {@link Producto}.
 *
 * <p>Extiende {@link ReactiveCrudRepository} que proporciona las operaciones
 * CRUD básicas como {@code Mono}/{@code Flux} sin necesidad de implementación manual.
 */
public interface ProductoRepository extends ReactiveCrudRepository<Producto, Long> {
}
