package com.cursosdedesarrollo.servicioproductos.messaging;

import com.cursosdedesarrollo.servicioproductos.service.ProductoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Consumidor Kafka del evento {@link PedidoCreado}.
 *
 * <p>Usa el modelo funcional de Spring Cloud Stream: un {@link Bean} de tipo
 * {@link Consumer}{@code <T>} se convierte automáticamente en un listener del binding
 * configurado en {@code spring.cloud.stream.bindings.procesarPedido-in-0}.
 *
 * <p>El nombre del bean ({@code procesarPedido}) determina el nombre del binding;
 * Spring Cloud Stream infiere {@code procesarPedido-in-0} como canal de entrada.
 *
 * <p>Al recibir el evento, llama a {@link ProductoService#decrementarStock} de forma reactiva
 * y suscribe el resultado para que se ejecute efectivamente (block() no es necesario
 * porque {@code subscribe()} fuerza la materialización del Mono).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class StockConsumer {

    private final ProductoService productoService;

    /**
     * Bean funcional que procesa cada evento {@link PedidoCreado} recibido en Kafka.
     *
     * <p>Spring Cloud Stream enlaza automáticamente este {@link Consumer} con el topic
     * definido en {@code spring.cloud.stream.bindings.procesarPedido-in-0.destination}.
     *
     * @return consumer que decrementa el stock del producto indicado en el evento
     */
    @Bean
    public Consumer<PedidoCreado> procesarPedido() {
        return evento -> {
            log.info("Evento recibido — pedidoId={} productoId={} cantidad={}",
                    evento.getPedidoId(), evento.getProductoId(), evento.getCantidad());

            productoService.decrementarStock(evento.getProductoId(), evento.getCantidad())
                    .doOnSuccess(p -> log.info("Stock actualizado — productoId={} stockActual={}",
                            p.getId(), p.getStock()))
                    .doOnError(e -> log.error("Error al actualizar stock — productoId={}",
                            evento.getProductoId(), e))
                    .subscribe();
        };
    }
}
