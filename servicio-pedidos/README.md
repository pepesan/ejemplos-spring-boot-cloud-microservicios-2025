# servicio-pedidos

Microservicio reactivo de gestión de pedidos. Al crear un pedido orquesta tres operaciones:
consulta el producto en `servicio-productos` (WebClient + Circuit Breaker), persiste el pedido
con estado `PENDIENTE` y publica un evento `PedidoCreado` en Kafka (StreamBridge) para que
`servicio-productos` decremente el stock de forma asíncrona.

## Stack

| Componente              | Tecnología                                        |
|-------------------------|---------------------------------------------------|
| Framework web           | Spring WebFlux (Netty)                            |
| Persistencia            | Spring Data R2DBC + H2 en memoria                |
| Llamada síncrona        | WebClient con Load Balancer (Eureka)              |
| Circuit Breaker         | Spring Cloud Circuit Breaker + Resilience4j       |
| Mensajería (publicar)   | Spring Cloud Stream · StreamBridge                |
| Registro de servicio    | Spring Cloud Netflix Eureka Client               |
| Configuración externa   | Spring Cloud Config Client                        |
| Monitorización          | Spring Boot Actuator                              |
| Tracing distribuido     | `spring-boot-starter-zipkin` (Micrometer Tracing + Brave + Zipkin) |
| Boilerplate             | Lombok                                            |

## Puerto

| Entorno    | Puerto |
|------------|--------|
| Desarrollo | 8084   |

## Endpoints REST

| Método    | Path                          | Descripción                                    | Respuesta exitosa |
|-----------|-------------------------------|------------------------------------------------|-------------------|
| `GET`     | `/pedidos`                    | Lista todos los pedidos                        | 200 + `Flux<Pedido>` |
| `GET`     | `/pedidos/{id}`               | Obtiene un pedido por id                       | 200 / 404         |
| `GET`     | `/pedidos/producto/{pid}`     | Lista pedidos de un producto concreto          | 200 + `Flux<Pedido>` |
| `POST`    | `/pedidos`                    | Crea un pedido y publica evento Kafka          | 201 + `Mono<Pedido>` |
| `PATCH`   | `/pedidos/{id}/estado`        | Actualiza el estado del pedido                 | 200 / 404         |
| `DELETE`  | `/pedidos/{id}`               | Elimina un pedido                              | 204 / 404         |

### Ejemplo de uso directo

```bash
# Listar pedidos
curl http://localhost:8084/pedidos

# Obtener pedido por id
curl http://localhost:8084/pedidos/1

# Pedidos de un producto
curl http://localhost:8084/pedidos/producto/1

# Crear pedido (publica evento PedidoCreado → stock se decrementa en servicio-productos)
curl -X POST http://localhost:8084/pedidos \
  -H "Content-Type: application/json" \
  -d '{"productoId":1,"cantidad":2}'

# Actualizar estado (PENDIENTE → CONFIRMADO / CANCELADO)
curl -X PATCH "http://localhost:8084/pedidos/1/estado?estado=CONFIRMADO"

# Eliminar pedido
curl -X DELETE http://localhost:8084/pedidos/1
```

### Ejemplo de uso a través del API Gateway (puerto 8090)

El gateway elimina el prefijo `/servicio-pedidos` antes de reenviar la petición.

```bash
# Listar pedidos vía gateway
curl http://localhost:8090/servicio-pedidos/pedidos

# Crear pedido vía gateway
curl -X POST http://localhost:8090/servicio-pedidos/pedidos \
  -H "Content-Type: application/json" \
  -d '{"productoId":2,"cantidad":1}'

# Actualizar estado vía gateway
curl -X PATCH "http://localhost:8090/servicio-pedidos/pedidos/1/estado?estado=CONFIRMADO"

# Eliminar pedido vía gateway
curl -X DELETE http://localhost:8090/servicio-pedidos/pedidos/1
```

## Flujo de creación de pedido

```
Cliente
  │
  ▼
POST /pedidos
  │
  ├─► ProductoClient.findById(productoId)   ← WebClient lb://servicio-productos
  │       └─ Circuit Breaker (Resilience4j)
  │           • CLOSED  → devuelve ProductoInfo
  │           • OPEN    → devuelve Mono.empty() (fallback, pedido sigue adelante)
  │
  ├─► PedidoRepository.save(pedido)          ← R2DBC H2
  │       estado = PENDIENTE
  │       fechaCreacion = now()
  │
  └─► StreamBridge.send("pedidos-creados-out-0", PedidoCreadoEvento)
          │
          ▼
      Topic Kafka: pedidos-creados
          │
          ▼
      servicio-productos  (Consumer<PedidoCreado>)
          └─► decrementa stock del producto
```

## Circuit Breaker

La llamada a `servicio-productos` está protegida por un circuit breaker de Resilience4j
configurado en `config-repo/servicio-pedidos/servicio-pedidos.yml`.

| Parámetro | Valor | Descripción |
|-----------|-------|-------------|
| `failure-rate-threshold` | 50 % | Porcentaje de fallos para abrir el circuito |
| `minimum-number-of-calls` | 5 | Mínimo de llamadas para evaluar el umbral |
| `wait-duration-in-open-state` | 10 s | Tiempo en estado OPEN antes de HALF_OPEN |

Cuando el circuito está **abierto** (servicio-productos caído), el pedido se crea
igualmente en estado `PENDIENTE` y el evento Kafka se publica. Cuando servicio-productos
se recupere, cualquier mensaje pendiente en Kafka decrementará el stock.

El estado del circuit breaker es visible en Actuator:

```bash
curl http://localhost:8084/actuator/health
```

## Mensajería Kafka

`servicio-pedidos` **publica** en el topic `pedidos-creados` usando `StreamBridge`.
`servicio-productos` **consume** ese mismo topic.

Payload del evento:
```json
{
  "pedidoId": 4,
  "productoId": 1,
  "cantidad": 2
}
```

Para que la mensajería funcione es necesario Kafka arrancado:
```bash
docker compose -f docker/compose.yaml up -d
```

Kafka UI en `http://localhost:9001` — permite ver los mensajes publicados en el topic.

## Configuración externa

El servicio carga su configuración desde:
```
config-repo/servicio-pedidos/servicio-pedidos.yml
```

| Propiedad | Valor | Descripción |
|-----------|-------|-------------|
| `server.port` | 8084 | Puerto HTTP |
| `spring.r2dbc.url` | `r2dbc:h2:mem:///pedidosdb` | BD reactiva en memoria |
| `spring.cloud.stream.bindings.pedidos-creados-out-0.destination` | `pedidos-creados` | Topic Kafka de salida |
| `resilience4j.circuitbreaker.instances.producto-cb.*` | ver YAML | Circuit breaker config |

## Tracing distribuido

Crear un pedido genera la cadena de trazas más completa del ecosistema. Una sola traza agrupa **5 spans** bajo el mismo `traceId`:

```
[RAIZ] [servicio-pedidos]  http post /pedidos          (~640 ms)
  [hijo] [servicio-pedidos]  http get                    (~113 ms)  ← WebClient saliente
  [hijo] [servicio-productos] http get /productos/{id}   (~2 ms)    ← servidor productos
  [hijo] [servicio-pedidos]  streambridge process        (~2 ms)    ← preparación Kafka
  [hijo] [servicio-pedidos]  pedidos-creados-out-0 send  (~11 ms)   ← envío al topic
```

| Propiedad | Valor | Fuente |
|---|---|---|
| `management.tracing.sampling.probability` | `1.0` (100 % en desarrollo) | `config-repo/application.yml` |
| `management.zipkin.tracing.endpoint` | `http://localhost:9411/api/v2/spans` | `config-repo/application.yml` |

### Cómo ver la traza en Zipkin

La UI usa filtros que se añaden uno a uno con el botón `+`. Sin el filtro por `spanName`, las llamadas al actuator (1 span cada 30 s) tapan la traza importante.

1. Abre http://localhost:9411
2. Pulsa **`+`** → elige **`serviceName`** → escribe `servicio-pedidos`
3. Pulsa **`+`** de nuevo → elige **`spanName`** → escribe `http post /pedidos`
4. Pulsa **Run Query**
5. Haz click en la entrada que muestre **5 spans**

```bash
# Genera un pedido para tener una traza reciente
curl -X POST http://localhost:8084/pedidos \
  -H "Content-Type: application/json" \
  -d '{"productoId":1,"cantidad":2}'
```

### Fixes necesarios en Spring Boot 4 para propagación correcta

Por defecto, en Spring Boot 4 con código reactivo (WebFlux + Reactor), el contexto de traza **no se propaga** a llamadas imperativas (como `StreamBridge.send()`) ni al `WebClient` creado con `@LoadBalanced`. Sin estos dos ajustes, cada operación genera su propio `traceId` y las trazas aparecen fragmentadas en Zipkin.

**Fix 1 — Propagación Reactor → ThreadLocal (`ServicioPedidosApplication.java`)**

```java
public static void main(String[] args) {
    Hooks.enableAutomaticContextPropagation();  // ← obligatorio
    SpringApplication.run(ServicioPedidosApplication.class, args);
}
```

Sin esto, `StreamBridge.send()` ejecutado dentro de `doOnSuccess` no ve el span padre del HTTP handler porque Brave usa ThreadLocal y el contexto de Reactor no se traslada automáticamente.

**Fix 2 — ObservationRegistry en el WebClient (`WebClientConfig.java`)**

```java
@Bean
@LoadBalanced
public WebClient.Builder loadBalancedWebClientBuilder(ObservationRegistry observationRegistry) {
    return WebClient.builder().observationRegistry(observationRegistry);
}
```

Sin esto, el `WebClient.Builder` creado manualmente con `@LoadBalanced` no hereda los customizers de tracing que Spring Boot aplica solo al builder auto-configurado. Cada llamada saliente inicia una traza nueva en lugar de continuar la del request entrante.

## Tests

Los tests de integración usan:
- **`@MockitoBean ProductoClient`** — sustituye el WebClient real; el test controla qué devuelve
  (nota: `@MockitoBean` es el reemplazo de `@MockBean` en Spring Framework 7.x / Spring Boot 4.x)
- **`TestChannelBinderConfiguration`** — sustituye el binder Kafka real
- **`OutputDestination`** — permite verificar los eventos publicados por `StreamBridge`

```bash
# Ejecutar tests
./gradlew :servicio-pedidos:test

# Clase concreta
./gradlew :servicio-pedidos:test --tests "com.cursosdedesarrollo.serviciopedidos.ServicioPedidosApplicationTest"
```

### Cobertura de los tests

| Test | Qué verifica |
|------|-------------|
| `contextLoads` | El contexto Spring arranca correctamente |
| `findAll_debeRetornarPedidosIniciales` | GET /pedidos devuelve datos del schema.sql |
| `findById_conIdExistente_debeRetornar200` | GET /pedidos/1 devuelve el pedido |
| `findById_conIdInexistente_debeRetornar404` | GET /pedidos/9999 devuelve 404 |
| `crear_debeGuardarPedidoYRetornar201` | POST crea pedido con estado PENDIENTE y fecha |
| `crear_debePublicarEventoKafka` | El evento PedidoCreado se publica en el topic Kafka |
| `crear_conProductoNoDisponible_debeCrearIgualmente` | Pedido se crea aunque el circuit breaker esté abierto |
| `findByProductoId_debeRetornarPedidosDelProducto` | GET /pedidos/producto/{id} filtra correctamente |
| `actualizarEstado_conIdExistente_debeActualizarEstado` | PATCH /pedidos/1/estado actualiza el estado |
| `actualizarEstado_conIdInexistente_debeRetornar404` | PATCH sobre id inexistente devuelve 404 |
| `deleteById_conIdExistente_debeRetornar204` | DELETE devuelve 204 |
| `deleteById_conIdInexistente_debeRetornar404` | DELETE sobre id inexistente devuelve 404 |

## Arranque

Requiere que estén arrancados previamente:

1. `eureka-server` (puerto 8761)
2. `config-server` (puerto 8888)
3. Kafka (puerto 9092) — `docker compose -f docker/compose.yaml up -d`
4. `servicio-productos` (puerto 8083) — opcional, el circuit breaker protege si está caído

```bash
./gradlew :servicio-pedidos:bootRun
```
