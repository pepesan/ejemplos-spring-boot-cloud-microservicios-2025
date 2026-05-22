package com.cursosdedesarrollo.consulpedidos.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("pedido")
public class Pedido {

    @Id
    private Long id;
    private Long productoId;
    private Integer cantidad;
    private BigDecimal total;
    private String estado;
}
