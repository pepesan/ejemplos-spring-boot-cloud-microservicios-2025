package com.cursosdedesarrollo.servicioproductos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servicio de gestión de productos.
 *
 * <p>Expone un CRUD reactivo sobre la entidad {@link domain.Producto} usando
 * Spring WebFlux y R2DBC con H2 en memoria. Se registra en Eureka y carga
 * su configuración desde el Config Server.
 *
 * <p>Además actúa como consumidor Kafka: escucha eventos {@code PedidoCreado}
 * en el topic {@code pedidos-creados} (publicados por {@code servicio-pedidos})
 * y decrementa el stock del producto correspondiente.
 */
@SpringBootApplication
public class ServicioProductosApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicioProductosApplication.class, args);
    }
}
