package com.cursosdedesarrollo.consulproductos;

import com.cursosdedesarrollo.consulproductos.messaging.PedidoCreado;
import com.cursosdedesarrollo.consulproductos.service.ProductoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestChannelBinderConfiguration.class)
@TestPropertySource(properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.config.import=",
        "spring.r2dbc.url=r2dbc:h2:mem:///testproductosdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.cloud.function.definition=procesarPedido",
        "spring.cloud.stream.bindings.procesarPedido-in-0.destination=pedidos-creados",
        "spring.cloud.stream.bindings.procesarPedido-in-0.group=test-group"
})
class ConsulProductosApplicationTest {

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private InputDestination inputDestination;

    @BeforeEach
    void setup() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void listarProductosDevuelveListaNoVacia() {
        client.get().uri("/productos")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class).hasSize(5);
    }

    @Test
    void obtenerProductoPorIdExistente() {
        client.get().uri("/productos/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.nombre").isNotEmpty();
    }

    @Test
    void obtenerProductoNoExistenteDevuelve404() {
        client.get().uri("/productos/999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void crearProductoDevuelve201() {
        Map<String, Object> body = Map.of(
                "nombre", "Webcam HD",
                "descripcion", "1080p 30fps",
                "precio", 45.99,
                "stock", 10
        );

        client.post().uri("/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.nombre").isEqualTo("Webcam HD");
    }

    @Test
    void actualizarProductoExistente() {
        Map<String, Object> body = Map.of(
                "nombre", "Teclado actualizado",
                "descripcion", "Nueva descripción",
                "precio", 99.99,
                "stock", 40
        );

        client.put().uri("/productos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.nombre").isEqualTo("Teclado actualizado")
                .jsonPath("$.precio").isEqualTo(99.99);
    }

    @Test
    void actualizarProductoNoExistenteDevuelve404() {
        Map<String, Object> body = Map.of(
                "nombre", "X", "descripcion", "X", "precio", 1.0, "stock", 1
        );

        client.put().uri("/productos/999")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void decrementarStockProducto() {
        client.patch().uri("/productos/2/stock?cantidad=5")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.stock").isEqualTo(115);
    }

    @Test
    void eliminarProductoExistente() {
        client.delete().uri("/productos/5")
                .exchange()
                .expectStatus().isNoContent();

        client.get().uri("/productos/5")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void procesarPedido_debeDecrementarStock() {
        int stockAntes = productoService.findById(3L).block().getStock();

        PedidoCreado evento = PedidoCreado.builder()
                .pedidoId(100L)
                .productoId(3L)
                .cantidad(5)
                .build();
        inputDestination.send(MessageBuilder.withPayload(evento).build(), "pedidos-creados");

        StepVerifier.create(productoService.findById(3L))
                .assertNext(p -> assertThat(p.getStock()).isEqualTo(stockAntes - 5))
                .verifyComplete();
    }

    @Test
    void procesarPedido_noBajaDeZero() {
        PedidoCreado evento = PedidoCreado.builder()
                .pedidoId(200L)
                .productoId(4L)
                .cantidad(9999)
                .build();
        inputDestination.send(MessageBuilder.withPayload(evento).build(), "pedidos-creados");

        StepVerifier.create(productoService.findById(4L))
                .assertNext(p -> assertThat(p.getStock()).isGreaterThanOrEqualTo(0))
                .verifyComplete();
    }
}
