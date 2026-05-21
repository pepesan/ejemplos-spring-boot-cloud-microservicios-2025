package com.cursosdedesarrollo.servicioproductos.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

/**
 * Entidad de dominio que representa un producto del catálogo.
 *
 * <p>Mapeada a la tabla {@code producto} en H2 (en memoria) mediante R2DBC.
 * El campo {@code id} se genera automáticamente por la base de datos
 * ({@code AUTO_INCREMENT}); al crear una entidad nueva debe ser {@code null}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("producto")
public class Producto {

    @Id
    private Long id;

    private String nombre;

    private String descripcion;

    private BigDecimal precio;

    /** Unidades disponibles en almacén. Se decrementa al recibir un evento {@code PedidoCreado}. */
    private Integer stock;
}
