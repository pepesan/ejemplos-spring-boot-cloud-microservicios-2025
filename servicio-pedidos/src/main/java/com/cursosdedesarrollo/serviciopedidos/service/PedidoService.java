package com.cursosdedesarrollo.serviciopedidos.service;

import com.cursosdedesarrollo.serviciopedidos.client.ProductoClient;
import com.cursosdedesarrollo.serviciopedidos.client.ProductoClientRestClient;
import com.cursosdedesarrollo.serviciopedidos.client.ProductoClientRestTemplate;
import com.cursosdedesarrollo.serviciopedidos.client.ProductoInfo;
import com.cursosdedesarrollo.serviciopedidos.domain.Pedido;
import com.cursosdedesarrollo.serviciopedidos.messaging.PedidoCreadoEvento;
import com.cursosdedesarrollo.serviciopedidos.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
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
    private final ProductoClientRestClient productoClientRestClient;
    private final ProductoClientRestTemplate productoClientRestTemplate;
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
     *   <li>Consulta el producto con circuit breaker. Si el circuito está abierto o el servicio
     *       no responde, el fallback devuelve {@code Mono.empty()} y se lanza 503 al cliente.</li>
     *   <li>Calcula el total (precio × cantidad).</li>
     *   <li>Persiste el pedido con estado {@code PENDIENTE}.</li>
     *   <li>Publica {@link PedidoCreadoEvento} en el binding {@code pedidos-creados-out-0}.</li>
     * </ol>
     *
     * @param pedido datos del pedido recibidos del cliente
     * @return el pedido guardado con su id generado
     * @throws ResponseStatusException 503 si {@code servicio-productos} no está disponible
     */
    public Mono<Pedido> crear(Pedido pedido) {
        return productoClient.findById(pedido.getProductoId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "servicio-productos no disponible — inténtalo de nuevo más tarde")))
                .doOnNext(p -> {
                    log.info("Producto encontrado — id={} nombre='{}' precio={}", p.getId(), p.getNombre(), p.getPrecio());
                    pedido.setTotal(p.getPrecio().multiply(BigDecimal.valueOf(pedido.getCantidad())));
                })
                .then(Mono.defer(() -> {
                    pedido.setId(null);
                    pedido.setEstado("PENDIENTE");
                    pedido.setFechaCreacion(LocalDateTime.now());
                    return repository.save(pedido);
                }))
                .doOnSuccess(this::publicarEvento);
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

    /**
     * Crea un pedido en <strong>modo resiliente</strong> (degradación graceful).
     *
     * <p>Si {@code servicio-productos} no está disponible o el circuit breaker está abierto,
     * el pedido se persiste igualmente con {@code total = null}. El evento {@code PedidoCreado}
     * se publica en Kafka para que cuando {@code servicio-productos} se recupere procese el
     * decremento de stock, manteniendo la consistencia eventual del sistema.
     *
     * <p>Contrasta con {@link #crear}, que rechaza la petición con 503 ante cualquier fallo
     * del servicio de productos (modo estricto).
     *
     * @param pedido datos del pedido recibidos del cliente
     * @return el pedido guardado, con {@code total} calculado si el servicio respondió o
     *         {@code null} si el circuit breaker actuó como fallback
     */
    public Mono<Pedido> crearResiliente(Pedido pedido) {
        return productoClient.findById(pedido.getProductoId())
                .doOnNext(p -> {
                    log.info("Producto encontrado — id={} nombre='{}' precio={}", p.getId(), p.getNombre(), p.getPrecio());
                    pedido.setTotal(p.getPrecio().multiply(BigDecimal.valueOf(pedido.getCantidad())));
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("Producto id={} no disponible (CB abierto) — pedido creado en modo offline, total=null",
                                pedido.getProductoId())))
                .then(Mono.defer(() -> {
                    pedido.setId(null);
                    pedido.setEstado("PENDIENTE");
                    pedido.setFechaCreacion(LocalDateTime.now());
                    return repository.save(pedido);
                }))
                .doOnSuccess(this::publicarEvento);
    }

    /**
     * Consulta un producto en {@code servicio-productos} usando {@link org.springframework.web.client.RestClient}
     * (cliente bloqueante moderno, Spring 6+).
     *
     * <p>La llamada bloqueante se envuelve en {@code Mono.fromCallable} y se ejecuta en el pool
     * {@code boundedElastic}, diseñado para operaciones de I/O bloqueante. Sin este desvío,
     * bloquear un hilo de Netty lanzaría {@code BlockingOperationError}.
     */
    public Mono<ProductoInfo> findProductoByIdRestClient(Long id) {
        return Mono.fromCallable(() -> productoClientRestClient.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt.map(Mono::just).orElse(Mono.empty()));
    }

    /**
     * Consulta un producto en {@code servicio-productos} usando {@link org.springframework.web.client.RestTemplate}
     * (cliente bloqueante clásico, disponible desde Spring 3).
     *
     * <p>Idéntico en comportamiento a {@link #findProductoByIdRestClient}: la diferencia es
     * la API del cliente HTTP, no el mecanismo de integración con el pipeline reactivo.
     */
    public Mono<ProductoInfo> findProductoByIdRestTemplate(Long id) {
        return Mono.fromCallable(() -> productoClientRestTemplate.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt.map(Mono::just).orElse(Mono.empty()));
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
