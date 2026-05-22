package com.cursosdedesarrollo.consulpedidos;

import com.cursosdedesarrollo.consulpedidos.client.ProductoClient;
import com.cursosdedesarrollo.consulpedidos.client.ProductoInfo;
import com.cursosdedesarrollo.consulpedidos.messaging.PedidoCreadoEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestChannelBinderConfiguration.class)
@TestPropertySource(properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.config.import=",
        "spring.r2dbc.url=r2dbc:h2:mem:///testpedidosdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.cloud.stream.bindings.pedidos-creados-out-0.destination=pedidos-creados",
        "spring.cloud.stream.source=pedidos-creados-out-0"
})
class ConsulPedidosApplicationTest {

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @Autowired
    private OutputDestination outputDestination;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    ProductoClient productoClient;

    @BeforeEach
    void setup() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        ProductoInfo producto = new ProductoInfo();
        producto.setId(1L);
        producto.setNombre("Teclado mecánico");
        producto.setPrecio(BigDecimal.valueOf(89.99));
        producto.setStock(50);
        when(productoClient.findById(1L)).thenReturn(Mono.just(producto));
        when(productoClient.findById(any(Long.class))).thenReturn(Mono.empty());
        when(productoClient.findById(1L)).thenReturn(Mono.just(producto));
    }

    @Test
    void contextLoads() {
    }

    @Test
    void listarPedidosVacio() {
        client.get().uri("/pedidos")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class).hasSize(0);
    }

    @Test
    void crearPedidoConProductoDisponible() {
        client.post().uri("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("productoId", 1, "cantidad", 2))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.productoId").isEqualTo(1)
                .jsonPath("$.cantidad").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(179.98)
                .jsonPath("$.estado").isEqualTo("PENDIENTE");
    }

    @Test
    void crearPedidoSinProducto_totalNull() {
        // productoId=99 → mock devuelve Mono.empty() (circuit breaker fallback)
        client.post().uri("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("productoId", 99, "cantidad", 1))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.productoId").isEqualTo(99)
                .jsonPath("$.total").doesNotExist()
                .jsonPath("$.estado").isEqualTo("PENDIENTE");
    }

    @Test
    void obtenerPedidoNoExistenteDevuelve404() {
        client.get().uri("/pedidos/999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void actualizarEstadoPedido() {
        // Crear primero
        WebTestClient.ResponseSpec resp = client.post().uri("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("productoId", 1, "cantidad", 1))
                .exchange()
                .expectStatus().isCreated();

        Long id = resp.returnResult(Map.class).getResponseBody()
                .blockFirst().get("id") instanceof Integer i ? i.longValue()
                : Long.valueOf(resp.returnResult(Map.class).getResponseBody().blockFirst().get("id").toString());

        client.patch().uri("/pedidos/{id}/estado?estado=CONFIRMADO", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("CONFIRMADO");
    }

    @Test
    void eliminarPedido() {
        // Crear
        Map<?, ?> creado = client.post().uri("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("productoId", 1, "cantidad", 1))
                .exchange()
                .expectStatus().isCreated()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst();

        Long id = Long.valueOf(creado.get("id").toString());

        client.delete().uri("/pedidos/{id}", id)
                .exchange()
                .expectStatus().isNoContent();

        client.get().uri("/pedidos/{id}", id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void crearPedidoPublicaEventoKafka() throws Exception {
        outputDestination.clear();

        client.post().uri("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("productoId", 1, "cantidad", 3))
                .exchange()
                .expectStatus().isCreated();

        Message<byte[]> mensaje = outputDestination.receive(1000, "pedidos-creados");
        assertThat(mensaje).isNotNull();

        PedidoCreadoEvento evento = objectMapper.readValue(mensaje.getPayload(), PedidoCreadoEvento.class);
        assertThat(evento.getProductoId()).isEqualTo(1L);
        assertThat(evento.getCantidad()).isEqualTo(3);
        assertThat(evento.getPedidoId()).isNotNull();
    }
}
