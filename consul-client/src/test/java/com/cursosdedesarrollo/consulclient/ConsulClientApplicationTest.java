package com.cursosdedesarrollo.consulclient;

import com.cursosdedesarrollo.consulclient.tarea.Tarea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests de integración del consul-client.
 *
 * Se deshabilita Consul (discovery + config) para que el test sea autónomo.
 * La BD H2 in-memory se crea con schema.sql al arrancar el contexto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.consul.config.enabled=false",
        "spring.config.import=",
        "spring.sql.init.mode=always",
        // CASE_INSENSITIVE_IDENTIFIERS: Spring Data R2DBC genera columnas en mayúsculas entre
        // comillas ("NOMBRE") que H2 2.x trata como case-sensitive sin este flag.
        "consulclient.datasource.url=r2dbc:h2:mem:///testconsuldb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
})
class ConsulClientApplicationTest {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    // Con Consul deshabilitado no se registra ningún ReactiveDiscoveryClient.
    // @MockitoBean proporciona uno vacío para que HolaController pueda arrancar.
    @MockitoBean
    ReactiveDiscoveryClient reactiveDiscoveryClient;

    @BeforeEach
    void setup() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        when(reactiveDiscoveryClient.getServices()).thenReturn(Flux.empty());
        when(reactiveDiscoveryClient.getInstances(org.mockito.ArgumentMatchers.anyString())).thenReturn(Flux.empty());
    }

    // -------------------------------------------------------------------------
    // Utilidad interna
    // -------------------------------------------------------------------------

    private Long crearTarea(String nombre, boolean completada) {
        return webTestClient.post().uri("/tareas")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Tarea.builder().nombre(nombre).descripcion("desc de test").completada(completada).build())
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Tarea.class)
                .returnResult()
                .getResponseBody()
                .getId();
    }

    // -------------------------------------------------------------------------
    // Contexto y endpoints de diagnóstico
    // -------------------------------------------------------------------------

    @Test
    void contextLoads() {
    }

    @Test
    void hola_debeResponder200() {
        webTestClient.get().uri("/hola")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(s -> assertThat(s).contains("Hola"));
    }

    @Test
    void config_debeRetornarDefaultsSinConsul() {
        webTestClient.get().uri("/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.entorno").isEqualTo("local");
    }

    @Test
    void dbConfig_debeRetornarH2PorDefecto() {
        webTestClient.get().uri("/db-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.url").value(url -> assertThat((String) url).contains("h2"))
                .jsonPath("$.username").isEqualTo("sa");
    }

    // -------------------------------------------------------------------------
    // CRUD /tareas
    // -------------------------------------------------------------------------

    @Test
    void crear_debeRetornar201ConIdGenerado() {
        Tarea nueva = Tarea.builder().nombre("Comprar leche").descripcion("Entera 2L").completada(false).build();

        webTestClient.post().uri("/tareas")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(nueva)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Tarea.class)
                .value(t -> {
                    assertThat(t.getId()).isNotNull();
                    assertThat(t.getNombre()).isEqualTo("Comprar leche");
                    assertThat(t.getCompletada()).isFalse();
                });
    }

    @Test
    void listar_debeRetornarListaNoVacia() {
        crearTarea("Tarea para listar", false);

        webTestClient.get().uri("/tareas")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Tarea.class)
                .value(list -> assertThat(list).isNotEmpty());
    }

    @Test
    void findById_conIdExistente_debeRetornar200() {
        Long id = crearTarea("Tarea por ID", false);

        webTestClient.get().uri("/tareas/" + id)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Tarea.class)
                .value(t -> assertThat(t.getId()).isEqualTo(id));
    }

    @Test
    void findById_conIdInexistente_debeRetornar404() {
        webTestClient.get().uri("/tareas/999999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void actualizar_debeModificarNombreYEstado() {
        Long id = crearTarea("Tarea original", false);
        Tarea cambios = Tarea.builder().nombre("Tarea modificada").descripcion("nueva desc").completada(true).build();

        webTestClient.put().uri("/tareas/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cambios)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Tarea.class)
                .value(t -> {
                    assertThat(t.getNombre()).isEqualTo("Tarea modificada");
                    assertThat(t.getCompletada()).isTrue();
                });
    }

    @Test
    void actualizar_conIdInexistente_debeRetornar404() {
        Tarea cambios = Tarea.builder().nombre("X").descripcion("X").completada(false).build();

        webTestClient.put().uri("/tareas/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cambios)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void eliminar_debeRetornar204YLaTareaDebeDesaparecer() {
        Long id = crearTarea("Tarea a eliminar", false);

        webTestClient.delete().uri("/tareas/" + id)
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get().uri("/tareas/" + id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void eliminar_conIdInexistente_debeRetornar404() {
        webTestClient.delete().uri("/tareas/999999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void filtrarPorCompletada_debeRetornarSoloLasCoincidentes() {
        crearTarea("Tarea pendiente", false);
        crearTarea("Tarea completada", true);

        webTestClient.get().uri("/tareas?completada=true")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Tarea.class)
                .value(list -> {
                    assertThat(list).isNotEmpty();
                    assertThat(list).allMatch(Tarea::getCompletada);
                });

        webTestClient.get().uri("/tareas?completada=false")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Tarea.class)
                .value(list -> assertThat(list).allMatch(t -> !t.getCompletada()));
    }
}
