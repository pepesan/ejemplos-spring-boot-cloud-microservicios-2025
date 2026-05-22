# consul-gateway

API Gateway del stack Consul. Actúa como punto de entrada único para todos los servicios registrados en Consul, enrutando peticiones mediante balanceo de carga reactivo (`lb://`).

Las rutas se leen del KV store de Consul al arrancar, lo que permite modificarlas sin recompilar ni reiniciar el gateway.

## Stack

| Tecnología                          | Uso                                                        |
|-------------------------------------|------------------------------------------------------------|
| Spring Boot 4.0.5 + WebFlux         | Runtime reactivo (Netty)                                   |
| Spring Cloud Gateway Server WebFlux | Enrutamiento y filtrado de peticiones                      |
| Spring Cloud Consul Discovery       | Resolución de instancias via `lb://nombre-servicio`        |
| Spring Cloud Consul Config          | Rutas y config leídas del KV store de Consul               |
| Micrometer Tracing + Zipkin         | Trazas distribuidas                                        |

## Puerto

`8091` — http://localhost:8091

## Prerrequisitos

**1. Consul Docker:**

```bash
docker compose -f docker/compose.yaml up -d consul
```

**2. Cargar las rutas del gateway en Consul KV** (obligatorio — sin este paso el gateway arranca sin rutas):

```bash
docker exec consul consul kv put config/consul-gateway/data \
'spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: consul-client-route
              uri: lb://consul-client
              predicates:
                - Path=/consul-client/**
              filters:
                - StripPrefix=1
            - id: consul-productos-route
              uri: lb://consul-productos
              predicates:
                - Path=/consul-productos/**
              filters:
                - StripPrefix=1
            - id: consul-pedidos-route
              uri: lb://consul-pedidos
              predicates:
                - Path=/consul-pedidos/**
              filters:
                - StripPrefix=1

management:
  tracing:
    sampling:
      probability: 1.0
    export:
      zipkin:
        endpoint: http://localhost:9411/api/v2/spans'
```

**3. Servicios del stack arrancados** (el gateway resuelve instancias vía Consul):

```bash
./gradlew :consul-client:bootRun &
./gradlew :consul-productos:bootRun &
./gradlew :consul-pedidos:bootRun &
```

Verificar: `curl http://localhost:8085/actuator/health` / `curl http://localhost:8086/actuator/health` / `curl http://localhost:8087/actuator/health`

## Arranque

```bash
./gradlew :consul-gateway:bootRun
```

Al arrancar el gateway:

1. Carga rutas y config desde `config/consul-gateway/data` en el KV de Consul.
2. Se registra en Consul con el nombre `consul-gateway`.
3. Declara un health-check HTTP: Consul hace poll a `/actuator/health` cada 10 s.

## Rutas configuradas

Las rutas viven en Consul KV (`config/consul-gateway/data`), no en el código:

| Ruta entrante             | Servicio destino        | Path reenviado |
|---------------------------|-------------------------|----------------|
| `/consul-client/**`       | `lb://consul-client`    | `/**`          |
| `/consul-productos/**`    | `lb://consul-productos` | `/**`          |
| `/consul-pedidos/**`      | `lb://consul-pedidos`   | `/**`          |

El filtro `StripPrefix=1` elimina el primer segmento del path antes de reenviar.

### Flujo de una petición

```
Cliente → GET /consul-client/hola
    → Gateway: predicado Path=/consul-client/**  ✓
    → Filtro StripPrefix=1 elimina /consul-client
    → LoadBalancer resuelve lb://consul-client → instancia sana en Consul
    → Petición reenviada a http://<ip>:8085/hola
```

## Endpoints de diagnóstico

```bash
# Estado del gateway
curl http://localhost:8091/actuator/health

# Rutas activas en tiempo de ejecución
curl http://localhost:8091/actuator/gateway/routes | jq
```

## Prueba manual con curl

Con el gateway y todos los servicios del stack arrancados:

```bash
# ── consul-client ──────────────────────────────────────────────
curl -s http://localhost:8091/consul-client/hola
curl -s http://localhost:8091/consul-client/config | jq
curl -s http://localhost:8091/consul-client/servicios | jq

# CRUD de tareas a través del gateway
curl -s -X POST http://localhost:8091/consul-client/tareas \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Tarea via gateway","descripcion":"Creada a través del consul-gateway","completada":false}' | jq

curl -s http://localhost:8091/consul-client/tareas | jq
curl -s -X DELETE -w "\nHTTP %{http_code}" http://localhost:8091/consul-client/tareas/1

# ── consul-productos ────────────────────────────────────────────
curl -s http://localhost:8091/consul-productos/productos | jq
curl -s http://localhost:8091/consul-productos/productos/1 | jq

curl -s -X POST http://localhost:8091/consul-productos/productos \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Hub USB-C","descripcion":"7 puertos","precio":39.99,"stock":60}' | jq

# ── consul-pedidos ──────────────────────────────────────────────
curl -s http://localhost:8091/consul-pedidos/pedidos | jq

curl -s -X POST http://localhost:8091/consul-pedidos/pedidos \
  -H 'Content-Type: application/json' \
  -d '{"productoId":1,"cantidad":2}' | jq

curl -s http://localhost:8091/consul-pedidos/pedidos | jq
```

---

## Configuración en Consul KV

Las rutas del gateway se almacenan en `config/consul-gateway/data`.

> **Importante:** En Spring Cloud Gateway 5.x (Spring Cloud Oakwood) el prefijo de rutas
> cambió de `spring.cloud.gateway.routes` a `spring.cloud.gateway.server.webflux.routes`.

### Cargar la configuración inicial del stack completo (CLI con Docker)

```bash
docker exec consul consul kv put config/consul-gateway/data \
'spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: consul-client-route
              uri: lb://consul-client
              predicates:
                - Path=/consul-client/**
              filters:
                - StripPrefix=1
            - id: consul-productos-route
              uri: lb://consul-productos
              predicates:
                - Path=/consul-productos/**
              filters:
                - StripPrefix=1
            - id: consul-pedidos-route
              uri: lb://consul-pedidos
              predicates:
                - Path=/consul-pedidos/**
              filters:
                - StripPrefix=1

management:
  tracing:
    sampling:
      probability: 1.0
    export:
      zipkin:
        endpoint: http://localhost:9411/api/v2/spans'
```

### Añadir una ruta nueva sin reiniciar

El watcher de Consul detecta cambios en el KV y el gateway recarga las rutas automáticamente. Basta con actualizar `config/consul-gateway/data` con el KV completo incluyendo la nueva ruta.

### Ver las rutas activas

```bash
curl -s http://localhost:8091/actuator/gateway/routes | jq '.[].route_id'
```

---

## Comparativa con el api-gateway (stack Eureka)

| Aspecto                   | `api-gateway` (Eureka)             | `consul-gateway` (Consul)            |
|---------------------------|------------------------------------|--------------------------------------|
| Discovery                 | `spring-cloud-starter-netflix-eureka-client` | `spring-cloud-starter-consul-discovery` |
| Config de rutas           | Config Server (`config-repo/`)     | Consul KV (`config/consul-gateway/data`) |
| Puerto                    | `8090`                             | `8091`                               |
| Registry UI               | http://localhost:8761              | http://localhost:8500/ui             |

---

## Configuración relevante en application.yml

```yaml
spring:
  config:
    import: "optional:consul:"      # rutas y config vienen del KV de Consul
  cloud:
    consul:
      host: localhost
      port: 8500
      discovery:
        prefer-ip-address: true
        health-check-path: /actuator/health
      config:
        format: YAML
        data-key: data
        prefixes:
          - config
        watch:
          enabled: true             # recarga rutas al cambiar el KV
```

---

## Tests

```bash
./gradlew :consul-gateway:test
```

El test desactiva Consul, discovery y las rutas para que el contexto arranque sin infraestructura externa.
