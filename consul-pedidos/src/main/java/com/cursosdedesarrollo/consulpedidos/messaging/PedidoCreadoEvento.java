package com.cursosdedesarrollo.consulpedidos.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoCreadoEvento {

    private Long pedidoId;
    private Long productoId;
    private Integer cantidad;
}
