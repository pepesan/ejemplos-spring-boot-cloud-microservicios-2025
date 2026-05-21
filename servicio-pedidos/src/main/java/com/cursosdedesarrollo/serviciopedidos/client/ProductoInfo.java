package com.cursosdedesarrollo.serviciopedidos.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO con la información mínima de un producto recibida desde {@code servicio-productos}.
 *
 * <p>Solo se mapean los campos necesarios para la lógica de negocio de pedidos;
 * cualquier campo extra en la respuesta se ignora (Jackson descarta lo desconocido).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoInfo {

    private Long id;
    private String nombre;
    private BigDecimal precio;
    private Integer stock;
}
