package com.cursosdedesarrollo.serviciopedidos.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento de dominio publicado cuando se confirma un pedido nuevo.
 *
 * <p>Se serializa como JSON y se envía al topic Kafka {@code pedidos-creados}
 * mediante {@code StreamBridge}. {@code servicio-productos} lo consume para
 * decrementar el stock del producto correspondiente.
 *
 * <p>La estructura es intencionadamente idéntica a la clase {@code PedidoCreado}
 * del lado consumidor. En producción ambas deberían compartir un módulo de contratos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoCreadoEvento {

    /** Identificador del pedido que origina el evento. */
    private Long pedidoId;

    /** Identificador del producto cuyo stock debe decrementarse. */
    private Long productoId;

    /** Cantidad de unidades pedidas. */
    private Integer cantidad;
}
