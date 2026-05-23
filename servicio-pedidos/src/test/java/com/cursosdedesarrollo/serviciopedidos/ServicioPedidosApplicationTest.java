package com.cursosdedesarrollo.serviciopedidos;

import com.cursosdedesarrollo.serviciopedidos.client.ProductoClient;
import com.cursosdedesarrollo.serviciopedidos.client.ProductoClientRestClient;
import com.cursosdedesarrollo.serviciopedidos.client.ProductoClientRestTemplate;
import com.cursosdedesarrollo.serviciopedidos.client.ProductoInfo;
import com.cursosdedesarrollo.serviciopedidos.domain.Pedido;
import com.cursosdedesarrollo.serviciopedidos.messaging.PedidoCreadoEvento;
import com.cursosdedesarrollo.serviciopedidos.service.PedidoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Tests de integración del servicio de pedidos.
 *
 * <p>{@link ProductoClient} se reemplaza por un {@link MockBean} para aislar
 * el comportamiento del servicio de pedidos sin necesitar {@code servicio-productos}
 * arrancado.
 *
 * <p>{@link TestChannelBinderConfiguration} sustituye el binder Kafka por un binder
 * en memoria, permitiendo verificar los eventos publicados con {@link OutputDestination}.
 *
 * <p>Eureka y Config Server se deshabilitan mediante {@link TestPropertySource}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestChannelBinderConfiguration.class)
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.config.import=",
        "spring.cloud.discovery.enabled=false",
        "spring.r2dbc.url=r2dbc:h2:mem:///testpedidosdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.sql.init.mode=always",
        "spring.cloud.stream.bindings.pedidos-creados-out-0.destination=pedidos-creados",
        "spring.cloud.stream.source=pedidos-creados-out-0"
})
class ServicioPedidosApplicationTest {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private OutputDestination outputDestination;

    @Autowired
    private ObjectMapper objectMapper;

    // Los tres clientes HTTP se sustituyen por mocks para aislar el test de servicio-productos.
    // @MockitoBean es el reemplazo de @MockBean en Spring Framework 7.x / Spring Boot 4.x.
    @MockitoBean
    private ProductoClient productoClient;

    @MockitoBean
    private ProductoClientRestClient productoClientRestClient;

    @MockitoBean
    private ProductoClientRestTemplate productoClientRestTemplate;

    @BeforeEach
    void setup() {
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        // Por defecto los tres mocks devuelven un producto válido
        ProductoInfo producto = new ProductoInfo(1L, "Teclado mecánico", new BigDecimal("89.99"), 50);
        when(productoClient.findById(anyLong())).thenReturn(Mono.just(producto));
        when(productoClientRestClient.findById(anyLong())).thenReturn(Optional.of(producto));
        when(productoClientRestTemplate.findById(anyLong())).thenReturn(Optional.of(producto));
    }

    @Test
    void contextLoads() {
        // Verifica que el contexto Spring arranca correctamente con todos sus beans
    }

    @Test
    void findAll_debeRetornarPedidosIniciales() {
        webTestClient.get()
                .uri("/pedidos")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Pedido.class)
                .value(list -> assertThat(list).hasSizeGreaterThanOrEqualTo(1));
    }

    @Test
    void findById_conIdExistente_debeRetornar200() {
        webTestClient.get()
                .uri("/pedidos/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Pedido.class)
                .value(p -> assertThat(p.getId()).isEqualTo(1L));
    }

    @Test
    void findById_conIdInexistente_debeRetornar404() {
        webTestClient.get()
                .uri("/pedidos/9999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void crear_debeGuardarPedidoYRetornar201() {
        Pedido nuevo = Pedido.builder()
                .productoId(2L)
                .cantidad(3)
                .build();

        webTestClient.post()
                .uri("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(nuevo)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Pedido.class)
                .value(p -> {
                    assertThat(p.getId()).isNotNull();
                    assertThat(p.getEstado()).isEqualTo("PENDIENTE");
                    assertThat(p.getFechaCreacion()).isNotNull();
                    // 89.99 × 3 = 269.97
                    assertThat(p.getTotal()).isEqualByComparingTo(new BigDecimal("269.97"));
                });
    }

    @Test
    void crear_debePublicarEventoKafka() throws Exception {
        // Limpia mensajes previos del canal
        outputDestination.clear();

        Pedido nuevo = Pedido.builder()
                .productoId(1L)
                .cantidad(2)
                .build();

        webTestClient.post()
                .uri("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(nuevo)
                .exchange()
                .expectStatus().isCreated();

        // OutputDestination.receive() usa el nombre del topic (destination), no del binding.
        // El topic "pedidos-creados" está configurado en spring.cloud.stream.bindings.pedidos-creados-out-0.destination
        Message<byte[]> mensaje = outputDestination.receive(1000, "pedidos-creados");
        assertThat(mensaje).isNotNull();

        PedidoCreadoEvento evento = objectMapper.readValue(mensaje.getPayload(), PedidoCreadoEvento.class);
        assertThat(evento.getProductoId()).isEqualTo(1L);
        assertThat(evento.getCantidad()).isEqualTo(2);
        assertThat(evento.getPedidoId()).isNotNull();
    }

    @Test
    void crear_conProductoNoDisponible_debeRetornar503() {
        // Simula el circuit breaker abierto: el cliente devuelve vacío
        when(productoClient.findById(99L)).thenReturn(Mono.empty());

        Pedido nuevo = Pedido.builder()
                .productoId(99L)
                .cantidad(1)
                .build();

        webTestClient.post()
                .uri("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(nuevo)
                .exchange()
                // El pedido se rechaza con 503 cuando servicio-productos no está disponible
                .expectStatus().isEqualTo(503);
    }

    @Test
    void findByProductoId_debeRetornarPedidosDelProducto() {
        webTestClient.get()
                .uri("/pedidos/producto/1")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Pedido.class)
                .value(list -> assertThat(list).allMatch(p -> p.getProductoId().equals(1L)));
    }

    @Test
    void actualizarEstado_conIdExistente_debeActualizarEstado() {
        webTestClient.patch()
                .uri("/pedidos/1/estado?estado=CONFIRMADO")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Pedido.class)
                .value(p -> assertThat(p.getEstado()).isEqualTo("CONFIRMADO"));
    }

    @Test
    void actualizarEstado_conIdInexistente_debeRetornar404() {
        webTestClient.patch()
                .uri("/pedidos/9999/estado?estado=CANCELADO")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteById_conIdExistente_debeRetornar204() {
        webTestClient.delete()
                .uri("/pedidos/3")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void deleteById_conIdInexistente_debeRetornar404() {
        webTestClient.delete()
                .uri("/pedidos/9999")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── Modo resiliente (degradación graceful) ───────────────────────────────

    @Test
    void crearResiliente_conProductoDisponible_debeCalcularTotalYRetornar201() {
        Pedido nuevo = Pedido.builder()
                .productoId(1L)
                .cantidad(3)
                .build();

        webTestClient.post()
                .uri("/pedidos/resiliente")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(nuevo)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Pedido.class)
                .value(p -> {
                    assertThat(p.getId()).isNotNull();
                    assertThat(p.getEstado()).isEqualTo("PENDIENTE");
                    // 89.99 × 3 = 269.97 — total calculado porque el servicio respondió
                    assertThat(p.getTotal()).isEqualByComparingTo(new BigDecimal("269.97"));
                });
    }

    @Test
    void crearResiliente_conProductoNoDisponible_debeCrearConTotalNullYRetornar201() {
        // Simula circuit breaker abierto: el cliente devuelve vacío
        when(productoClient.findById(99L)).thenReturn(Mono.empty());

        Pedido nuevo = Pedido.builder()
                .productoId(99L)
                .cantidad(1)
                .build();

        webTestClient.post()
                .uri("/pedidos/resiliente")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(nuevo)
                .exchange()
                // El pedido se crea igualmente aunque el servicio no esté disponible
                .expectStatus().isCreated()
                .expectBody(Pedido.class)
                .value(p -> {
                    assertThat(p.getId()).isNotNull();
                    assertThat(p.getEstado()).isEqualTo("PENDIENTE");
                    // total es null porque no se pudo consultar el precio
                    assertThat(p.getTotal()).isNull();
                });
    }

    // ── Clientes bloqueantes: RestClient ──────────────────────────────────────

    @Test
    void findProductoRestClient_conIdExistente_debeRetornar200ConDatosDelProducto() {
        webTestClient.get()
                .uri("/pedidos/demo/producto/1/restclient")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductoInfo.class)
                .value(p -> {
                    assertThat(p.getId()).isEqualTo(1L);
                    assertThat(p.getNombre()).isEqualTo("Teclado mecánico");
                    assertThat(p.getPrecio()).isEqualByComparingTo(new BigDecimal("89.99"));
                });
    }

    @Test
    void findProductoRestClient_conServicioNoDisponible_debeRetornar404() {
        when(productoClientRestClient.findById(99L)).thenReturn(Optional.empty());

        webTestClient.get()
                .uri("/pedidos/demo/producto/99/restclient")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── Clientes bloqueantes: RestTemplate ───────────────────────────────────

    @Test
    void findProductoRestTemplate_conIdExistente_debeRetornar200ConDatosDelProducto() {
        webTestClient.get()
                .uri("/pedidos/demo/producto/1/resttemplate")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductoInfo.class)
                .value(p -> {
                    assertThat(p.getId()).isEqualTo(1L);
                    assertThat(p.getNombre()).isEqualTo("Teclado mecánico");
                    assertThat(p.getPrecio()).isEqualByComparingTo(new BigDecimal("89.99"));
                });
    }

    @Test
    void findProductoRestTemplate_conServicioNoDisponible_debeRetornar404() {
        when(productoClientRestTemplate.findById(99L)).thenReturn(Optional.empty());

        webTestClient.get()
                .uri("/pedidos/demo/producto/99/resttemplate")
                .exchange()
                .expectStatus().isNotFound();
    }
}
