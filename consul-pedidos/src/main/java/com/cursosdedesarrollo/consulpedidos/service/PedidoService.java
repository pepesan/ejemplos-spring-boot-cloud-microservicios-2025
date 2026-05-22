package com.cursosdedesarrollo.consulpedidos.service;

import com.cursosdedesarrollo.consulpedidos.client.ProductoClient;
import com.cursosdedesarrollo.consulpedidos.domain.Pedido;
import com.cursosdedesarrollo.consulpedidos.messaging.PedidoCreadoEvento;
import com.cursosdedesarrollo.consulpedidos.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository repository;
    private final ProductoClient productoClient;
    private final StreamBridge streamBridge;

    public Flux<Pedido> findAll() {
        return repository.findAll();
    }

    public Mono<Pedido> findById(Long id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado: " + id)));
    }

    public Mono<Pedido> crear(Long productoId, Integer cantidad) {
        return productoClient.findById(productoId)
                .map(p -> {
                    BigDecimal total = p.getPrecio().multiply(BigDecimal.valueOf(cantidad));
                    return Pedido.builder()
                            .productoId(productoId)
                            .cantidad(cantidad)
                            .total(total)
                            .estado("PENDIENTE")
                            .build();
                })
                .switchIfEmpty(Mono.fromSupplier(() -> Pedido.builder()
                        .productoId(productoId)
                        .cantidad(cantidad)
                        .estado("PENDIENTE")
                        .build()))
                .flatMap(repository::save)
                .doOnSuccess(this::publicarEvento);
    }

    public Mono<Pedido> actualizarEstado(Long id, String estado) {
        return findById(id).flatMap(p -> {
            p.setEstado(estado);
            return repository.save(p);
        });
    }

    public Mono<Void> deleteById(Long id) {
        return findById(id).flatMap(repository::delete);
    }

    private void publicarEvento(Pedido pedido) {
        PedidoCreadoEvento evento = PedidoCreadoEvento.builder()
                .pedidoId(pedido.getId())
                .productoId(pedido.getProductoId())
                .cantidad(pedido.getCantidad())
                .build();
        streamBridge.send("pedidos-creados-out-0", evento);
        log.info("Evento PedidoCreado publicado — pedidoId={} productoId={} cantidad={}",
                pedido.getId(), pedido.getProductoId(), pedido.getCantidad());
    }
}
