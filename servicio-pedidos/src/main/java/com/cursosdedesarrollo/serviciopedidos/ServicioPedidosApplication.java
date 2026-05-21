package com.cursosdedesarrollo.serviciopedidos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servicio de gestión de pedidos.
 *
 * <p>Expone un CRUD reactivo sobre la entidad {@link domain.Pedido} usando
 * Spring WebFlux y R2DBC con H2 en memoria.
 *
 * <p>Al crear un pedido:
 * <ol>
 *   <li>Consulta la información del producto en {@code servicio-productos} mediante
 *       WebClient con load balancing (Eureka) y Circuit Breaker (Resilience4j).</li>
 *   <li>Guarda el pedido en la base de datos con estado {@code PENDIENTE}.</li>
 *   <li>Publica un evento {@code PedidoCreado} en el topic Kafka {@code pedidos-creados}
 *       mediante {@code StreamBridge}, que {@code servicio-productos} consumerá para
 *       decrementar el stock de forma asíncrona.</li>
 * </ol>
 *
 * <p>Si {@code servicio-productos} no está disponible, el circuit breaker abre y
 * el pedido se crea igualmente (consistencia eventual): el evento Kafka actualizará
 * el stock cuando el servicio de productos se recupere.
 */
@SpringBootApplication
public class ServicioPedidosApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicioPedidosApplication.class, args);
    }
}
