package com.cursosdedesarrollo.serviciopedidos.service;

import com.cursosdedesarrollo.serviciopedidos.client.ProductoClient;
import com.cursosdedesarrollo.serviciopedidos.domain.Pedido;
import com.cursosdedesarrollo.serviciopedidos.messaging.PedidoCreadoEvento;
import com.cursosdedesarrollo.serviciopedidos.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Lógica de negocio para la gestión de pedidos.
 *
 * <p>El método {@link #crear} orquesta tres operaciones:
 * <ol>
 *   <li>Consulta la información del producto en {@code servicio-productos} vía
 *       {@link ProductoClient} (WebClient + Circuit Breaker). Si el servicio está
 *       caído el resultado es vacío y el pedido se crea igualmente.</li>
 *   <li>Persiste el pedido con estado {@code PENDIENTE}.</li>
 *   <li>Publica un evento {@link PedidoCreadoEvento} en Kafka mediante
 *       {@link StreamBridge} para que {@code servicio-productos} decremente el stock
 *       de forma asíncrona.</li>
 * </ol>
 */
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
        return repository.findById(id);
    }

    public Flux<Pedido> findByProductoId(Long productoId) {
        return repository.findByProductoId(productoId);
    }

    /**
     * Crea un pedido nuevo, publica el evento Kafka y devuelve el pedido persistido.
     *
     * <p>Flujo:
     * <ol>
     *   <li>Consulta el producto con circuit breaker; si está caído continúa con vacío.</li>
     *   <li>Inicializa los campos del pedido (id nulo, estado PENDIENTE, fecha actual).</li>
     *   <li>Guarda el pedido.</li>
     *   <li>Publica {@link PedidoCreadoEvento} en el binding {@code pedidos-creados-out-0}.</li>
     * </ol>
     *
     * @param pedido datos del pedido recibidos del cliente
     * @return el pedido guardado con su id generado
     */
    public Mono<Pedido> crear(Pedido pedido) {
        return productoClient.findById(pedido.getProductoId())
                .doOnNext(p -> log.info("Producto encontrado — id={} nombre='{}' stock={}",
                        p.getId(), p.getNombre(), p.getStock()))
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("Producto id={} no disponible — pedido se crea en modo offline",
                                pedido.getProductoId())))
                .then(Mono.defer(() -> {
                    pedido.setId(null);
                    pedido.setEstado("PENDIENTE");
                    pedido.setFechaCreacion(LocalDateTime.now());
                    return repository.save(pedido);
                }))
                .doOnSuccess(saved -> publicarEvento(saved));
    }

    public Mono<Pedido> actualizarEstado(Long id, String nuevoEstado) {
        return repository.findById(id)
                .flatMap(p -> {
                    p.setEstado(nuevoEstado);
                    return repository.save(p);
                });
    }

    public Mono<Void> deleteById(Long id) {
        return repository.deleteById(id);
    }

    private void publicarEvento(Pedido pedido) {
        PedidoCreadoEvento evento = PedidoCreadoEvento.builder()
                .pedidoId(pedido.getId())
                .productoId(pedido.getProductoId())
                .cantidad(pedido.getCantidad())
                .build();
        // El binding "pedidos-creados-out-0" está declarado en servicio-pedidos.yml.
        // StreamBridge envía el mensaje de forma imperativa sin necesitar un Supplier funcional.
        streamBridge.send("pedidos-creados-out-0", evento);
        log.info("Evento PedidoCreado publicado — pedidoId={} productoId={} cantidad={}",
                pedido.getId(), pedido.getProductoId(), pedido.getCantidad());
    }
}
