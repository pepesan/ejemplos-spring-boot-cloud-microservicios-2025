# consul-productos

CRUD reactivo de productos del stack Consul. Proporciona datos de catálogo a otros microservicios (como `consul-pedidos`) a través del `consul-gateway`.

## Stack

| Tecnología                          | Uso                                                          |
|-------------------------------------|--------------------------------------------------------------|
| Spring Boot 4.0.5 + WebFlux         | Runtime reactivo (Netty)                                     |
| Spring Data R2DBC + H2              | Persistencia reactiva (H2 en dev, MySQL en prod)             |
| Spring Cloud Consul Discovery       | Registro y descubrimiento de instancias                      |
| Spring Cloud Consul Config          | Configuración desde Consul KV                                |
| Spring Cloud Stream + Kafka         | Consumidor del evento `PedidoCreado` para decrementar stock  |
| Micrometer Tracing + Zipkin         | Trazas distribuidas                                          |

## Puerto

`8086` — http://localhost:8086

## Prerrequisitos

**1. Consul, Zipkin y Kafka Docker:**

```bash
docker compose -f docker/compose.yaml up -d consul zipkin kafka
```

**2. Cargar configuración compartida de Zipkin** (aplica a todos los servicios Consul):

```bash
docker exec consul consul kv put config/application/data \
'management:
  tracing:
    sampling:
      probability: 1.0
    export:
      zipkin:
        endpoint: http://localhost:9411/api/v2/spans'
```

**3. Cargar configuración del servicio en Consul KV:**

```bash
docker exec consul consul kv put config/consul-productos/data \
'spring:
  r2dbc:
    url: r2dbc:h2:mem:///productosdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
    username: sa
    password: ""
  cloud:
    function:
      definition: procesarPedido
    stream:
      bindings:
        procesarPedido-in-0:
          destination: pedidos-creados
          group: consul-productos-group
      kafka:
        binder:
          brokers: localhost:9092'
```

> **Nota sobre prioridad:** Si la clave existe en Consul KV, sus valores sobreescriben los de `application.yml`. Si la clave no existe, `application.yml` actúa como fallback y la aplicación arranca igualmente. Este comportamiento es automático en Spring Cloud Consul Config.

## Arranque

```bash
./gradlew :consul-productos:bootRun
```

Al arrancar:
1. Lee configuración de `config/application/data` (Zipkin compartido) y `config/consul-productos/data`.
2. Se registra en Consul con el nombre `consul-productos`.
3. Declara un health-check HTTP: Consul hace poll a `/actuator/health` cada 10 s.
4. Liquibase aplica las migraciones (crea la tabla `producto` e inserta 5 productos de ejemplo).

## Endpoints

| Método   | Ruta                         | Descripción                                        |
|----------|------------------------------|----------------------------------------------------|
| `GET`    | `/productos`                 | Lista todos los productos                          |
| `GET`    | `/productos/{id}`            | Obtiene un producto por ID (404 si no existe)      |
| `POST`   | `/productos`                 | Crea un producto nuevo (201 Created)               |
| `PUT`    | `/productos/{id}`            | Actualiza un producto completo (404 si no existe)  |
| `DELETE` | `/productos/{id}`            | Elimina un producto (204 No Content)               |
| `PATCH`  | `/productos/{id}/stock`      | Decrementa stock: `?cantidad=N`                    |

## Prueba manual con curl

```bash
# Listar todos
curl -s http://localhost:8086/productos | jq

# Obtener por ID
curl -s http://localhost:8086/productos/1 | jq

# Crear
curl -s -X POST http://localhost:8086/productos \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Webcam 4K","descripcion":"60fps, autofoco","precio":129.99,"stock":25}' | jq

# Actualizar
curl -s -X PUT http://localhost:8086/productos/1 \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Teclado MX Red","descripcion":"Actualizado","precio":95.00,"stock":45}' | jq

# Decrementar stock
curl -s -X PATCH "http://localhost:8086/productos/1/stock?cantidad=3" | jq .stock

# Eliminar
curl -s -X DELETE -w "\nHTTP %{http_code}" http://localhost:8086/productos/6
```

## A través del consul-gateway

Con `consul-gateway` arrancado y la ruta cargada en el KV:

```bash
curl -s http://localhost:8091/consul-productos/productos | jq
curl -s http://localhost:8091/consul-productos/productos/1 | jq
curl -s -X POST http://localhost:8091/consul-productos/productos \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Hub USB-C","descripcion":"7 puertos","precio":39.99,"stock":60}' | jq
```

## Configuración en Consul KV

Spring Cloud Consul Config carga las claves del KV **antes** de que arranque el contexto Spring, con **mayor prioridad** que `application.yml`. Esto permite:

- **Sin Consul KV** → `application.yml` actúa como fallback (la app funciona igualmente).
- **Con Consul KV** → los valores del KV sobreescriben `application.yml` (útil para apuntar al broker Kafka real, la BBDD de producción, etc.).

### `config/consul-productos/data`

```yaml
spring:
  r2dbc:
    url: r2dbc:h2:mem:///productosdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
    username: sa
    password: ""
  cloud:
    function:
      definition: procesarPedido
    stream:
      bindings:
        procesarPedido-in-0:
          destination: pedidos-creados
          group: consul-productos-group
      kafka:
        binder:
          brokers: localhost:9092
```

### `config/application/data` (compartido con todos los servicios Consul)

Todos los servicios Consul leen esta clave. Equivalente a `config-repo/application.yml` en el stack Eureka.

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
    export:
      zipkin:
        endpoint: http://localhost:9411/api/v2/spans
```

### Ruta del gateway (en `config/consul-gateway/data`)

```yaml
- id: consul-productos-route
  uri: lb://consul-productos
  predicates:
    - Path=/consul-productos/**
  filters:
    - StripPrefix=1
```

## Trazas distribuidas

Con Zipkin corriendo, cada petición genera un span visible en http://localhost:9411.

Cuando `consul-pedidos` llama a este servicio vía `lb://consul-productos`, ambos spans aparecen enlazados bajo el mismo `traceId`.

## Mensajería Kafka

`consul-productos` consume el evento `PedidoCreado` publicado por `consul-pedidos` cuando se crea un pedido nuevo. Al recibir el evento, decrementa el stock del producto indicado (nunca por debajo de 0).

**Binding:** `procesarPedido-in-0` → topic `pedidos-creados`

**Flujo:**

```
consul-pedidos crea un pedido
  → publica PedidoCreadoEvento en Kafka (topic: pedidos-creados)
    → consul-productos recibe el evento
      → StockConsumer.procesarPedido() decrementa stock del producto
```

El stock nunca baja de 0 (protegido con `Math.max(0, stock - cantidad)`).

## Migraciones de base de datos (Liquibase)

El esquema se gestiona con **Liquibase** (no con `schema.sql`). Los changelogs se encuentran en `src/main/resources/db/changelog/db.changelog-master.yaml` e incluyen:

- **changeSet 1**: crea la tabla `producto` (id, nombre, descripcion, precio, stock)
- **changeSet 2**: inserta 5 productos iniciales de ejemplo

Liquibase requiere JDBC internamente. La clase `LiquibaseConfig` deriva la URL JDBC desde `spring.r2dbc.url` de forma que cambiar la BD en Consul KV actualiza automáticamente ambas conexiones. Las migraciones se ejecutan al arrancar sin intervención manual.

> **Importante:** `spring.r2dbc.username=sa` debe estar configurado (en `application.yml` o en Consul KV) para que R2DBC y Liquibase usen el mismo propietario de BD H2.

## Tests

```bash
./gradlew :consul-productos:test
```

11 tests de integración con `@SpringBootTest(webEnvironment = RANDOM_PORT)`:
- Los tests de Kafka usan `TestChannelBinderConfiguration` + `InputDestination` en lugar del broker real.
- Consul y discovery se desactivan; se usa H2 in-memory aislada (`testproductosdb`).
- Liquibase crea el esquema al arrancar el contexto de test.
