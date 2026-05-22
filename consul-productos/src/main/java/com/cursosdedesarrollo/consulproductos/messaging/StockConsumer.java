package com.cursosdedesarrollo.consulproductos.messaging;

import com.cursosdedesarrollo.consulproductos.service.ProductoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class StockConsumer {

    private final ProductoService productoService;

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
