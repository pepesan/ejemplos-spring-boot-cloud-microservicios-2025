package com.cursosdedesarrollo.consulpedidos.client;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class ProductoInfo {
    private Long id;
    private String nombre;
    private BigDecimal precio;
    private Integer stock;
}
