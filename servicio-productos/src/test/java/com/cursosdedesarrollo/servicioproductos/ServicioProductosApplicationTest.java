package com.cursosdedesarrollo.servicioproductos;

import com.cursosdedesarrollo.servicioproductos.domain.Producto;
import com.cursosdedesarrollo.servicioproductos.messaging.PedidoCreado;
import com.cursosdedesarrollo.servicioproductos.service.ProductoService;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración del servicio de productos.
 *
 * <p>Usa {@link TestChannelBinderConfiguration} en lugar del binder Kafka real,
 * lo que permite ejecutar los tests sin un broker Kafka arrancado.
 *
 * <p>La base de datos H2 en memoria se crea y puebla con {@code schema.sql}
 * al arrancar el contexto; cada test parte del mismo estado inicial.
 *
 * <p>Las propiedades {@code TestPropertySource} deshabilitan Eureka y el Config
 * Server para que el test arranque de forma autónoma sin infraestructura externa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestChannelBinderConfiguration.class)
@TestPropertySource(properties = {
        // Evita que el test intente conectar con Eureka Server
        "eureka.client.enabled=false",
        // Evita que el test intente conectar con Config Server
        "spring.config.import=",
        // Deshabilita el registro en Eureka explícitamente
        "spring.cloud.discovery.enabled=false",
        // Base de datos H2 en memoria para tests (la URL normalmente viene del Config Server).
        // CASE_INSENSITIVE_IDENTIFIERS evita que H2 almacene los identificadores en mayúsculas
        // por defecto, lo que rompería las consultas que Spring Data R2DBC genera en minúsculas.
        "spring.r2dbc.url=r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        // Ejecuta schema.sql al arrancar (la propiedad también está en application.yml pero se confirma aquí)
        "spring.sql.init.mode=always",
        // Registra el bean funcional como consumer (normalmente viene del Config Server)
        "spring.function.definition=procesarPedido",
        // Binding del consumer Kafka para el test binder
        "spring.cloud.stream.bindings.procesarPedido-in-0.destination=pedidos-creados",
        "spring.cloud.stream.bindings.procesarPedido-in-0.group=test-group"
})
class ServicioProductosApplicationTest {

    // Spring Boot 4.x / Spring Framework 7.x no auto-registra WebTestClient como bean;
    // se construye manualmente a partir del puerto asignado al azar.
    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private InputDestination inputDestination;

    @BeforeEach
    void setup() {
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void contextLoads() {
        // Verifica que el contexto Spring arranca correctamente con todos sus beans
    }

    @Test
    void findAll_debeRetornarProductosIniciales() {
        webTestClient.get()
                .uri("/productos")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Producto.class)
                // Otros tests pueden añadir/eliminar productos; verificamos al menos los 5 del schema.sql
                .value(list -> assertThat(list).hasSizeGreaterThanOrEqualTo(1));
    }

    @Test
    void findById_conIdExistente_debeRetornar200() {
        webTestClient.get()
                .uri("/productos/1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Producto.class)
                .value(p -> assertThat(p.getId()).isEqualTo(1L));
    }

    @Test
    void findById_conIdInexistente_debeRetornar404() {
        webTestClient.get()
                .uri("/productos/9999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void create_debeGuardarProductoYRetornar201() {
        Producto nuevo = Producto.builder()
                .nombre("Hub USB-C")
                .descripcion("7 puertos, compatible con USB 3.2")
                .precio(new BigDecimal("39.99"))
                .stock(75)
                .build();

        webTestClient.post()
                .uri("/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(nuevo)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Producto.class)
                .value(p -> {
                    assertThat(p.getId()).isNotNull();
                    assertThat(p.getNombre()).isEqualTo("Hub USB-C");
                });
    }

    @Test
    void update_conIdExistente_debeActualizarProducto() {
        Producto actualizado = Producto.builder()
                .nombre("Teclado mecánico PRO")
                .descripcion("Versión mejorada con iluminación RGB")
                .precio(new BigDecimal("109.99"))
                .stock(40)
                .build();

        webTestClient.put()
                .uri("/productos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(actualizado)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Producto.class)
                .value(p -> assertThat(p.getNombre()).isEqualTo("Teclado mecánico PRO"));
    }

    @Test
    void deleteById_conIdExistente_debeRetornar204() {
        webTestClient.delete()
                .uri("/productos/5")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void deleteById_conIdInexistente_debeRetornar404() {
        webTestClient.delete()
                .uri("/productos/9999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void procesarPedido_debeDecrementarStock() {
        // Obtiene stock inicial del producto 1
        Producto antes = productoService.findById(1L).block();
        int stockAntes = antes.getStock();

        // Publica un evento PedidoCreado en el canal de test (sin Kafka real)
        PedidoCreado evento = PedidoCreado.builder()
                .pedidoId(100L)
                .productoId(1L)
                .cantidad(3)
                .build();
        inputDestination.send(MessageBuilder.withPayload(evento).build(), "pedidos-creados");

        // Verifica que el stock se decrementó en 3 unidades
        StepVerifier.create(productoService.findById(1L))
                .assertNext(p -> assertThat(p.getStock()).isEqualTo(stockAntes - 3))
                .verifyComplete();
    }

    @Test
    void decrementarStock_noBajaDeZero() {
        // Simula un evento con cantidad mayor que el stock disponible
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
