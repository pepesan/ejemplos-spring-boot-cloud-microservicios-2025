package com.cursosdedesarrollo.consulpedidos.repository;

import com.cursosdedesarrollo.consulpedidos.domain.Pedido;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface PedidoRepository extends ReactiveCrudRepository<Pedido, Long> {
}
