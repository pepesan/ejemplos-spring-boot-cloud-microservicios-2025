package com.cursosdedesarrollo.consulproductos.repository;

import com.cursosdedesarrollo.consulproductos.domain.Producto;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ProductoRepository extends ReactiveCrudRepository<Producto, Long> {
}
