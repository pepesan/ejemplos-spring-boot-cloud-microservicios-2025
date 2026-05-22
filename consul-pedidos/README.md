# consul-pedidos

Servicio de pedidos del stack Consul. Llama a `consul-productos` vía `lb://consul-productos` para calcular el total de cada pedido, usando el catálogo de Consul para resolver la instancia.

Demuestra comunicación reactiva entre microservicios con Circuit Breaker y propagación de trazas Zipkin.

## Stack

| Tecnología                              | Uso                                                                      |
|-----------------------------------------|--------------------------------------------------------------------------|
| Spring Boot 4.0.5 + WebFlux             | Runtime reactivo (Netty)                                                 |
| Spring Data R2DBC + H2                  | Persistencia reactiva (H2 en dev, MySQL en prod)                         |
| Spring Cloud Consul Discovery           | Registro propio + resolución de `lb://consul-productos`                 |
| Spring Cloud Consul Config              | Configuración desde Consul KV                                            |
| Resilience4j Circuit Breaker reactivo   | Protege la llamada a `consul-productos`; fallback si no responde         |
| Spring Cloud Stream + Kafka             | Publica `PedidoCreadoEvento` en Kafka al crear un pedido                 |
| Micrometer Tracing + Zipkin             | Trazas distribuidas; propaga `traceId` al WebClient saliente             |

## Puerto

`8087` — http://localhost:8087

## Prerrequisitos

**1. Consul, Zipkin, Kafka y `consul-productos` arrancados:**

```bash
docker compose -f docker/compose.yaml up -d consul zipkin kafka
./gradlew :consul-productos:bootRun
```

Ver el README de `consul-productos` para cargar su configuración en el KV.

**2. Cargar configuración compartida Zipkin** (si no se hizo ya):

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
docker exec consul consul kv put config/consul-pedidos/data \
'spring:
  r2dbc:
    url: r2dbc:h2:mem:///pedidosdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
    username: sa
    password: ""
  cloud:
    stream:
      bindings:
        pedidos-creados-out-0:
          destination: pedidos-creados
      source: pedidos-creados-out-0
      kafka:
        binder:
          brokers: localhost:9092'
```

> **Nota sobre prioridad:** Si la clave existe en Consul KV, sus valores sobreescriben los de `application.yml`. Si la clave no existe (Consul no arrancado o KV vacío), `application.yml` actúa como fallback y la aplicación arranca igualmente. Este comportamiento es automático en Spring Cloud Consul Config — no requiere código adicional.

## Arranque

```bash
./gradlew :consul-pedidos:bootRun
```

Al arrancar:
1. Lee config de `config/application/data` (Zipkin) y `config/consul-pedidos/data`.
2. Se registra en Consul con el nombre `consul-pedidos`.
3. El `ProductoClient` resuelve `lb://consul-productos` usando el catálogo de Consul.

## Endpoints

| Método   | Ruta                           | Descripción                                               |
|----------|--------------------------------|-----------------------------------------------------------|
| `GET`    | `/pedidos`                     | Lista todos los pedidos                                   |
| `GET`    | `/pedidos/{id}`                | Obtiene un pedido por ID (404 si no existe)               |
| `POST`   | `/pedidos`                     | Crea pedido; consulta `consul-productos` para el total    |
| `PATCH`  | `/pedidos/{id}/estado`         | Actualiza estado: `?estado=CONFIRMADO` / `CANCELADO`      |
| `DELETE` | `/pedidos/{id}`                | Elimina un pedido (204 No Content)                        |

## Flujo al crear un pedido

```
POST /pedidos {"productoId":1,"cantidad":2}
  → ProductoClient.findById(1)
      → lb://consul-productos resuelto por Consul → GET http://192.168.x.x:8086/productos/1
      → precio = 89.99
  → total = 89.99 × 2 = 179.98
  → Pedido guardado en H2 con total=179.98, estado=PENDIENTE
```

Si `consul-productos` no responde (circuit breaker abierto):
```
  → fallback: Mono.empty()
  → Pedido guardado con total=null, estado=PENDIENTE
```

## Prueba manual con curl

```bash
# Crear pedido (calcula total consultando consul-productos)
curl -s -X POST http://localhost:8087/pedidos \
  -H 'Content-Type: application/json' \
  -d '{"productoId":1,"cantidad":2}' | jq .

# Listar pedidos
curl -s http://localhost:8087/pedidos | jq

# Actualizar estado
curl -s -X PATCH "http://localhost:8087/pedidos/1/estado?estado=CONFIRMADO" | jq .estado

# Eliminar pedido
curl -s -X DELETE -w "\nHTTP %{http_code}" http://localhost:8087/pedidos/1
```

## A través del consul-gateway

```bash
# Crear pedido
curl -s -X POST http://localhost:8091/consul-pedidos/pedidos \
  -H 'Content-Type: application/json' \
  -d '{"productoId":1,"cantidad":3}' | jq .

# Listar pedidos
curl -s http://localhost:8091/consul-pedidos/pedidos | jq
```

## Configuración en Consul KV

Spring Cloud Consul Config carga las claves del KV **antes** de que arranque el contexto Spring, con **mayor prioridad** que `application.yml`. Esto permite:

- **Sin Consul KV** → `application.yml` actúa como fallback (la app funciona igualmente).
- **Con Consul KV** → los valores del KV sobreescriben `application.yml` (útil para apuntar al broker Kafka real, la BBDD de producción, etc.).

### `config/consul-pedidos/data`

```yaml
spring:
  r2dbc:
    url: r2dbc:h2:mem:///pedidosdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
    username: sa
    password: ""
  cloud:
    stream:
      bindings:
        pedidos-creados-out-0:
          destination: pedidos-creados
      source: pedidos-creados-out-0
      kafka:
        binder:
          brokers: localhost:9092
```

### `config/application/data` (compartido con todos los servicios Consul)

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
- id: consul-pedidos-route
  uri: lb://consul-pedidos
  predicates:
    - Path=/consul-pedidos/**
  filters:
    - StripPrefix=1
```

## Trazas distribuidas (Zipkin)

Con Zipkin corriendo en http://localhost:9411, al crear un pedido se genera una traza con 4 spans enlazados:

```
[RAIZ] consul-gateway   http post /consul-pedidos/**
  [hijo] consul-pedidos http post /pedidos
    [hijo] consul-pedidos → WebClient saliente a consul-productos
    [hijo] consul-productos http get /productos/{id}
```

La propagación del `traceId` al WebClient se consigue inyectando `ObservationRegistry` en `WebClientConfig`:

```java
@Bean @LoadBalanced
public WebClient.Builder loadBalancedWebClientBuilder(ObservationRegistry registry) {
    return WebClient.builder().observationRegistry(registry);
}
```

### Ver la traza en Zipkin

1. Abre http://localhost:9411
2. Filtro `serviceName` → `consul-pedidos`
3. Filtro `spanName` → `http post /pedidos`
4. **Run Query** → click en la fila con 4 spans

## Circuit Breaker

El circuit breaker `consul-productos-cb` protege la llamada a `consul-productos`. Si el servicio no está disponible:

- El pedido se crea con `total: null`
- En Zipkin aparece el span marcado con error

Para probar el fallback: detener `consul-productos` y crear un pedido.

## Mensajería Kafka

Al crear un pedido, `consul-pedidos` publica un evento `PedidoCreadoEvento` en el topic `pedidos-creados` mediante `StreamBridge`. `consul-productos` lo consume para decrementar el stock del producto.

**Binding:** `pedidos-creados-out-0` → topic `pedidos-creados`

**Flujo completo:**

```
POST /pedidos {"productoId":1,"cantidad":2}
  → consul-productos consultado vía lb://consul-productos → precio=89.99
  → total = 89.99 × 2 = 179.98
  → Pedido guardado (estado=PENDIENTE)
  → StreamBridge publica PedidoCreadoEvento {pedidoId, productoId:1, cantidad:2}
    → consul-productos recibe el evento → decrementa stock de producto 1 en 2 unidades
```

## Migraciones de base de datos (Liquibase)

El esquema se gestiona con **Liquibase** (no con `schema.sql`). El changelog está en `src/main/resources/db/changelog/db.changelog-master.yaml` e incluye la creación de la tabla `pedido` (id, productoId, cantidad, total, estado).

Liquibase requiere JDBC internamente. La clase `LiquibaseConfig` deriva la URL JDBC desde `spring.r2dbc.url`. Al arrancar, las migraciones se ejecutan automáticamente.

> **Importante:** `spring.r2dbc.username=sa` debe estar configurado (en `application.yml` o en Consul KV) para que R2DBC y Liquibase usen el mismo propietario de BD H2.

## Tests

```bash
./gradlew :consul-pedidos:test
```

8 tests de integración:
- `ProductoClient` se mockea con `@MockitoBean` para no depender de `consul-productos` real.
- Los tests de Kafka usan `TestChannelBinderConfiguration` + `OutputDestination` para verificar el evento publicado.
- Consul y discovery se desactivan; H2 in-memory aislada (`testpedidosdb`).
- Liquibase crea el esquema al arrancar el contexto de test.
