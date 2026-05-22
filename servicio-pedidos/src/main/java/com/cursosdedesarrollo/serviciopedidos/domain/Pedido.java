package com.cursosdedesarrollo.serviciopedidos.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad de dominio que representa un pedido.
 *
 * <p>Mapeada a la tabla {@code pedido} en H2 en memoria mediante R2DBC.
 * La columna {@code producto_id} referencia lógicamente al producto en
 * {@code servicio-productos}, pero sin clave foránea real (servicios desacoplados).
 *
 * <p>El campo {@code estado} sigue el ciclo: {@code PENDIENTE} → {@code CONFIRMADO}
 * o {@code CANCELADO}. Todo pedido nuevo nace con estado {@code PENDIENTE}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("pedido")
public class Pedido {

    @Id
    private Long id;

    /** Identificador del producto solicitado (referencia lógica a servicio-productos). */
    private Long productoId;

    /** Unidades pedidas. */
    private Integer cantidad;

    /** Importe total (precio × cantidad). Null si servicio-productos no está disponible. */
    private BigDecimal total;

    /**
     * Estado del ciclo de vida: PENDIENTE, CONFIRMADO, CANCELADO.
     * Se establece a PENDIENTE al crear y puede actualizarse por otros flujos.
     */
    private String estado;

    /** Momento en que se registró el pedido. Se asigna automáticamente al crear. */
    private LocalDateTime fechaCreacion;
}
