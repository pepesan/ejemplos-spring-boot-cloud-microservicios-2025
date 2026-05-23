# servicio-pedidos

Microservicio reactivo de gestión de pedidos. Al crear un pedido orquesta tres operaciones:
consulta el producto en `servicio-productos` (WebClient + Circuit Breaker), persiste el pedido
con estado `PENDIENTE` y publica un evento `PedidoCreado` en Kafka (StreamBridge) para que
`servicio-productos` decremente el stock de forma asíncrona.

Incluye además dos endpoints de demostración que ilustran cómo integrar clientes HTTP bloqueantes
(`RestClient` y `RestTemplate`) dentro de un pipeline reactivo WebFlux.

## Arranque rápido

### Dependencias previas

| Servicio            | Puerto | Obligatorio |
|---------------------|--------|-------------|
| `eureka-server`     | 8761   | Sí          |
| `config-server`     | 8888   | Sí          |
| Kafka (Docker)      | 9092   | Sí          |
| `servicio-productos`| 8083   | No (todos los endpoints tienen circuit breaker con fallback vacío) |

```bash
# 1. Infraestructura Docker (Kafka + Kafka UI + Zipkin)
docker compose -f docker/compose.yaml up -d

# 2. Arranca en orden
./gradlew :eureka-server:bootRun
./gradlew :config-server:bootRun
./gradlew :servicio-productos:bootRun   # opcional pero recomendado

# 3. Arranca este servicio
./gradlew :servicio-pedidos:bootRun
```

Verificar que está registrado: http://localhost:8761 → debe aparecer `SERVICIO-PEDIDOS`.

---

## Stack

| Componente              | Tecnología                                        |
|-------------------------|---------------------------------------------------|
| Framework web           | Spring WebFlux (Netty)                            |
| Persistencia            | Spring Data R2DBC + H2 en memoria                |
| Cliente HTTP reactivo   | `WebClient` con Load Balancer (Eureka)            |
| Cliente HTTP bloqueante | `RestClient` (Spring 6+) y `RestTemplate`         |
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

### CRUD de pedidos

| Método    | Path                          | Descripción                                    | Respuesta exitosa |
|-----------|-------------------------------|------------------------------------------------|-------------------|
| `GET`     | `/pedidos`                    | Lista todos los pedidos                        | 200 + `Flux<Pedido>` |
| `GET`     | `/pedidos/{id}`               | Obtiene un pedido por id                       | 200 / 404         |
| `GET`     | `/pedidos/producto/{pid}`     | Lista pedidos de un producto concreto          | 200 + `Flux<Pedido>` |
| `POST`    | `/pedidos`                    | Crea un pedido — **modo estricto** (503 si CB abierto) | 201 / 503 |
| `POST`    | `/pedidos/resiliente`         | Crea un pedido — **modo resiliente** (total=null si CB abierto) | 201 |
| `PATCH`   | `/pedidos/{id}/estado`        | Actualiza el estado del pedido                 | 200 / 404         |
| `DELETE`  | `/pedidos/{id}`               | Elimina un pedido                              | 204 / 404         |

### Demostración de clientes HTTP bloqueantes

| Método | Path                                      | Cliente HTTP      | Descripción                                |
|--------|-------------------------------------------|-------------------|--------------------------------------------|
| `GET`  | `/pedidos/demo/producto/{id}/restclient`  | `RestClient`      | Consulta bloqueante (Spring 6+) desde pipeline reactivo |
| `GET`  | `/pedidos/demo/producto/{id}/resttemplate`| `RestTemplate`    | Consulta bloqueante (clásica) desde pipeline reactivo   |

Ambos endpoints devuelven `ProductoInfo` (200) o 404 si el producto no existe o el circuit breaker devuelve fallback vacío (servicio caído o circuito abierto).

### Ejemplos de uso directo

```bash
# Listar pedidos
curl http://localhost:8084/pedidos

# Obtener pedido por id
curl http://localhost:8084/pedidos/1

# Pedidos de un producto
curl http://localhost:8084/pedidos/producto/1

# ── Modo estricto: 503 si servicio-productos no está disponible ──────────────
# El pedido NO se crea si el circuit breaker está abierto.
# Garantía: todos los pedidos almacenados tienen 'total' calculado.
curl -s -X POST http://localhost:8084/pedidos \
  -H "Content-Type: application/json" \
  -d '{"productoId":1,"cantidad":2}' | jq .

# ── Modo resiliente: el pedido se crea aunque el CB esté abierto ─────────────
# Si servicio-productos no responde → total=null, el evento Kafka se publica
# igualmente → consistencia eventual: el stock se decrementa cuando el servicio
# se recupere y procese los mensajes pendientes.
curl -X POST http://localhost:8084/pedidos/resiliente \
  -H "Content-Type: application/json" \
  -d '{"productoId":1,"cantidad":2}'

# Actualizar estado (PENDIENTE → CONFIRMADO / CANCELADO)
curl -X PATCH "http://localhost:8084/pedidos/1/estado?estado=CONFIRMADO"

# Eliminar pedido
curl -X DELETE http://localhost:8084/pedidos/1

# Consulta bloqueante con RestClient (Spring 6+)
# -o >(jq .) envía el body a jq; -w imprime el código HTTP sin mezclarse con el JSON
curl -s -o >(jq . 2>/dev/null) -w "HTTP: %{http_code}\n" http://localhost:8084/pedidos/demo/producto/1/restclient

# Consulta bloqueante con RestTemplate (clásico)
curl -s -o >(jq . 2>/dev/null) -w "HTTP: %{http_code}\n" http://localhost:8084/pedidos/demo/producto/1/resttemplate
```

### Ejemplos de uso a través del API Gateway (puerto 8090)

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

# Clientes bloqueantes vía gateway
curl -s -o >(jq . 2>/dev/null) -w "HTTP: %{http_code}\n" http://localhost:8090/servicio-pedidos/pedidos/demo/producto/1/restclient
curl -s -o >(jq . 2>/dev/null) -w "HTTP: %{http_code}\n" http://localhost:8090/servicio-pedidos/pedidos/demo/producto/1/resttemplate
```

## Clientes HTTP bloqueantes en un pipeline reactivo

`servicio-pedidos` incluye dos clientes HTTP bloqueantes como ejemplo didáctico:

| Cliente                      | API             | Disponible desde | Recomendado          |
|------------------------------|-----------------|------------------|----------------------|
| `ProductoClientRestClient`   | `RestClient`    | Spring 6 / SB 3  | Sí (sustituto moderno de RestTemplate) |
| `ProductoClientRestTemplate` | `RestTemplate`  | Spring 3         | Mantenimiento (funcional, legado)       |

### Por qué envolver la llamada bloqueante

Netty (el servidor de WebFlux) usa un pool de hilos de event loop. Llamar a `.block()` o a un cliente HTTP bloqueante directamente desde un operador reactivo en esos hilos lanza `BlockingOperationError`.

La solución es delegar la ejecución a `Schedulers.boundedElastic()`, un pool diseñado para I/O bloqueante:

```java
// ✅ Correcto — la llamada bloqueante se ejecuta fuera del event loop
Mono.fromCallable(() -> clienteBloquente.findById(id))
    .subscribeOn(Schedulers.boundedElastic())
    .flatMap(opt -> opt.map(Mono::just).orElse(Mono.empty()))

// ❌ Incorrecto — bloquea el hilo de Netty
webClient.get().uri(...).retrieve().bodyToMono(X.class).block()
```

### Circuit Breaker en clientes bloqueantes

`ProductoClientRestClient` y `ProductoClientRestTemplate` usan `CircuitBreakerFactory` (versión
**bloqueante**), la contraparte de `ReactiveCircuitBreakerFactory` que usa `ProductoClient`.
Los tres comparten el id `producto-cb` y por tanto la misma configuración de Resilience4j.

| Cliente | Factory | Método `run()` | Retorno del fallback |
|---|---|---|---|
| `ProductoClient` (WebClient) | `ReactiveCircuitBreakerFactory` | `cb.run(Mono, fallback)` | `Mono.empty()` |
| `ProductoClientRestClient` | `CircuitBreakerFactory` | `cb.run(Supplier, fallback)` | `Optional.empty()` |
| `ProductoClientRestTemplate` | `CircuitBreakerFactory` | `cb.run(Supplier, fallback)` | `Optional.empty()` |

Si el circuito está abierto o `servicio-productos` falla, ambos clientes bloqueantes devuelven
`Optional.empty()` → el pipeline reactivo emite `Mono.empty()` → el controlador responde 404.

### Beans `@LoadBalanced` y la trampa del `RestClient.Builder`

`RestTemplate` se declara con `@LoadBalanced` en `WebClientConfig`:

```java
@Bean @LoadBalanced
public RestTemplate restTemplate() { return new RestTemplate(); }
```

Para `RestClient`, **no** se usa `@LoadBalanced RestClient.Builder` como bean. El motivo:
Spring Boot auto-configura `RestClient.Builder` con `@ConditionalOnMissingBean`. Si se declara
un bean `@LoadBalanced RestClient.Builder`, Spring Boot no crea el suyo, y el cliente interno
de Eureka (que también necesita `RestClient.Builder`) acaba usando el `@LoadBalanced`, lo que
hace que Eureka intente resolver `localhost` como nombre de servicio → falla con
`No instances available for service: localhost`.

En su lugar, `ProductoClientRestClient` añade el `LoadBalancerInterceptor` manualmente:

```java
this.restClient = restClientBuilder
    .baseUrl("http://servicio-productos")
    .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
    .build();
```

El resultado es idéntico en tiempo de ejecución, sin el conflicto con el cliente de Eureka.

## Dos patrones de circuit breaker con WebClient reactivo

Ambos endpoints usan el mismo `ProductoClient` (WebClient + Resilience4j). La diferencia
está en cómo el servicio reacciona cuando el fallback devuelve `Mono.empty()`.

### Modo estricto — `POST /pedidos`

> Prioridad: **integridad del dato** sobre disponibilidad.

```
POST /pedidos
  │
  ├─► ProductoClient.findById()   WebClient + CB Resilience4j
  │       • CLOSED → ProductoInfo → calcula total
  │       • OPEN   → Mono.empty()
  │                     └─► switchIfEmpty → Mono.error(503)
  │                                            └─► responde 503 al cliente
  │                                                pedido NO se persiste
  │
  └─► (solo si CLOSED) save() → Kafka → stock decrementa
```

Todos los pedidos almacenados tienen `total` calculado. El cliente sabe que debe reintentar.

### Modo resiliente — `POST /pedidos/resiliente`

> Prioridad: **disponibilidad** y consistencia eventual sobre integridad inmediata.

```
POST /pedidos/resiliente
  │
  ├─► ProductoClient.findById()   WebClient + CB Resilience4j
  │       • CLOSED → ProductoInfo → calcula total
  │       • OPEN   → Mono.empty()
  │                     └─► switchIfEmpty → log.warn + continúa
  │
  ├─► save(pedido)   total = calculado | null
  │       estado = PENDIENTE
  │
  └─► Kafka: PedidoCreadoEvento (siempre se publica)
          └─► cuando servicio-productos se recupere,
              procesa los mensajes Kafka pendientes
              └─► decrementa stock (consistencia eventual)
```

El pedido se persiste aunque `total` sea `null`. El evento Kafka garantiza que el stock
se decrementará en cuanto `servicio-productos` vuelva a estar disponible.

## Circuit Breaker

La llamada a `servicio-productos` está protegida por un circuit breaker de Resilience4j
configurado en `config-repo/servicio-pedidos/servicio-pedidos.yml`.

| Parámetro | Valor | Descripción |
|-----------|-------|-------------|
| `failure-rate-threshold` | 50 % | Porcentaje de fallos para abrir el circuito |
| `minimum-number-of-calls` | 5 | Mínimo de llamadas antes de evaluar el umbral |
| `wait-duration-in-open-state` | 10 s | Tiempo en estado OPEN antes de pasar a HALF_OPEN |
| `permitted-number-of-calls-in-half-open-state` | 3 | Llamadas de prueba en HALF_OPEN |

El fallback siempre devuelve `Mono.empty()`. Lo que varía entre los dos endpoints es
cómo el servicio reacciona a ese vacío:

| Endpoint | Reacción a `Mono.empty()` | Pedido persistido |
|---|---|---|
| `POST /pedidos` | `switchIfEmpty → 503` | No |
| `POST /pedidos/resiliente` | `switchIfEmpty → log + continúa` | Sí (total=null) |

El estado del circuit breaker es visible en el endpoint dedicado de Resilience4j:

```bash
curl -s http://localhost:8084/actuator/circuitbreakers | jq .
```

Respuesta de ejemplo con el circuito cerrado (funcionando con normalidad):
```json
{
  "circuitBreakers": {
    "producto-cb": {
      "state": "CLOSED",
      "bufferedCalls": 4,
      "failedCalls": 1,
      "failureRate": "-1.0%",
      "failureRateThreshold": "50.0%",
      "notPermittedCalls": 0
    }
  }
}
```

> `management.health.circuitbreakers.enabled` no funciona en Spring Boot 4 + Resilience4j 2.3.0
> (incompatibilidad en la API de health). Usar siempre `/actuator/circuitbreakers`.

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
- **`@MockitoBean ProductoClient`** — sustituye el WebClient reactivo; el test controla qué devuelve
- **`@MockitoBean ProductoClientRestClient`** — sustituye el cliente RestClient bloqueante
- **`@MockitoBean ProductoClientRestTemplate`** — sustituye el cliente RestTemplate bloqueante
  (nota: `@MockitoBean` es el reemplazo de `@MockBean` en Spring Framework 7.x / Spring Boot 4.x)
- **`TestChannelBinderConfiguration`** — sustituye el binder Kafka real
- **`OutputDestination`** — permite verificar los eventos publicados por `StreamBridge`

```bash
# Ejecutar todos los tests
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
| `crear_conProductoNoDisponible_debeRetornar503` | POST /pedidos devuelve 503 cuando el circuit breaker está abierto |
| `crearResiliente_conProductoDisponible_debeCalcularTotalYRetornar201` | POST /pedidos/resiliente calcula total cuando el servicio responde |
| `crearResiliente_conProductoNoDisponible_debeCrearConTotalNullYRetornar201` | POST /pedidos/resiliente crea el pedido con total=null cuando el CB está abierto |
| `findByProductoId_debeRetornarPedidosDelProducto` | GET /pedidos/producto/{id} filtra correctamente |
| `actualizarEstado_conIdExistente_debeActualizarEstado` | PATCH /pedidos/1/estado actualiza el estado |
| `actualizarEstado_conIdInexistente_debeRetornar404` | PATCH sobre id inexistente devuelve 404 |
| `deleteById_conIdExistente_debeRetornar204` | DELETE devuelve 204 |
| `deleteById_conIdInexistente_debeRetornar404` | DELETE sobre id inexistente devuelve 404 |
| `findProductoRestClient_conIdExistente_debeRetornar200ConDatosDelProducto` | GET demo/restclient devuelve ProductoInfo |
| `findProductoRestClient_conServicioNoDisponible_debeRetornar404` | GET demo/restclient devuelve 404 si servicio caído |
| `findProductoRestTemplate_conIdExistente_debeRetornar200ConDatosDelProducto` | GET demo/resttemplate devuelve ProductoInfo |
| `findProductoRestTemplate_conServicioNoDisponible_debeRetornar404` | GET demo/resttemplate devuelve 404 si servicio caído |
