package com.cursosdedesarrollo.servicioproductos.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento de dominio publicado por {@code servicio-pedidos} cuando se confirma un pedido.
 *
 * <p>Este DTO viaja serializado como JSON por el topic Kafka {@code pedidos-creados}.
 * {@code servicio-productos} lo consume para decrementar el stock del producto correspondiente.
 *
 * <p>Es intencionadamente simple: solo transporta los datos mínimos necesarios
 * para que el consumidor pueda actuar sin consultar otros servicios.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoCreado {

    /** Identificador único del pedido que originó el evento. */
    private Long pedidoId;

    /** Identificador del producto cuyo stock debe decrementarse. */
    private Long productoId;

    /** Cantidad de unidades pedidas. */
    private Integer cantidad;
}
