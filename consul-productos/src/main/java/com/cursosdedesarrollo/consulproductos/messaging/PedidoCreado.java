package com.cursosdedesarrollo.consulproductos.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoCreado {

    private Long pedidoId;
    private Long productoId;
    private Integer cantidad;
}
