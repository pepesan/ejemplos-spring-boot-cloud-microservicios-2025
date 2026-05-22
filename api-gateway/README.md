# api-gateway

Punto de entrada único al ecosistema de microservicios. 
Recibe todas las peticiones externas y las enruta al servicio interno correspondiente, 
resolviendo la instancia destino mediante el registro de Eureka.

## Stack

| Tecnología | Detalle |
|---|---|
| Spring Cloud Gateway (WebFlux) | `spring-cloud-starter-gateway-server-webflux` |
| Spring Cloud Netflix Eureka Client | `spring-cloud-starter-netflix-eureka-client` |
| Spring Cloud Config Client | `spring-cloud-starter-config` |
| Spring Boot Actuator | `spring-boot-starter-actuator` |
| Tracing distribuido | `spring-boot-starter-zipkin` (Micrometer Tracing + Brave + Zipkin) |
| Puerto | `8090` |

> **Cambios en Spring Cloud Gateway 5.x (Oakwood, 2025.1.1):**
> - El starter reactivo fue renombrado de `spring-cloud-starter-gateway` a `spring-cloud-starter-gateway-server-webflux`.
> - El prefijo de configuración cambió de `spring.cloud.gateway` a `spring.cloud.gateway.server.webflux`. Todas las rutas, filtros y predicados definidos en YAML deben usar el nuevo prefijo.

## Requisitos previos

Servicios que deben estar arrancados antes:

1. `eureka-server` en `localhost:8761`
2. `config-server` en `localhost:8888`
3. Los servicios destino de las rutas configuradas

## Arranque

```bash
./gradlew :api-gateway:bootRun
```

## Rutas configuradas

Las rutas viven en `config-repo/api-gateway/api-gateway.yml` y se cargan desde el Config Server al arrancar.

| ID de ruta | Entrada (cliente externo) | Destino interno | Path reenviado |
|---|---|---|---|
| `ruta-eureka-client` | `/eureka-client/**` | `lb://eureka-client` | `/**` (sin prefijo) |
| `ruta-config-client` | `/config-client/**` | `lb://config-client` | `/**` (sin prefijo) |
| `ruta-servicio-productos` | `/servicio-productos/**` | `lb://servicio-productos` | `/**` (sin prefijo) |
| `ruta-servicio-pedidos` | `/servicio-pedidos/**` | `lb://servicio-pedidos` | `/**` (sin prefijo) |

### Cómo funciona una petición

```
Cliente → GET http://localhost:8090/eureka-client/hola
    1. Predicado Path=/eureka-client/**  →  coincide ✓
    2. Filtro StripPrefix=1             →  elimina /eureka-client del path
    3. URI lb://eureka-client           →  LoadBalancer consulta Eureka
    4. Instancia resuelta               →  http://192.168.x.x:8081
    5. Petición reenviada               →  GET http://192.168.x.x:8081/hola
```

### Probar las rutas

```bash
# --- eureka-client ---
curl http://localhost:8090/eureka-client/
curl http://localhost:8090/eureka-client/hola
curl http://localhost:8090/eureka-client/servicios
curl http://localhost:8090/eureka-client/instancias

# --- config-client ---
curl http://localhost:8090/config-client/config

# --- servicio-productos (CRUD) ---
# Listar todos los productos
curl http://localhost:8090/servicio-productos/productos

# Obtener producto por id (404 si no existe)
curl http://localhost:8090/servicio-productos/productos/1

# Crear producto (devuelve 201)
curl -X POST http://localhost:8090/servicio-productos/productos \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Hub USB-C","descripcion":"7 puertos USB 3.2","precio":39.99,"stock":75}'

# Actualizar producto (404 si no existe)
curl -X PUT http://localhost:8090/servicio-productos/productos/1 \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Teclado mecánico PRO","descripcion":"RGB","precio":109.99,"stock":40}'

# Eliminar producto (204 si existe, 404 si no)
curl -X DELETE http://localhost:8090/servicio-productos/productos/1

# --- servicio-pedidos ---
# Listar pedidos
curl http://localhost:8090/servicio-pedidos/pedidos

# Crear pedido (publica evento PedidoCreado → stock decrementado en servicio-productos)
curl -X POST http://localhost:8090/servicio-pedidos/pedidos \
  -H "Content-Type: application/json" \
  -d '{"productoId":1,"cantidad":2}'

# Actualizar estado del pedido
curl -X PATCH "http://localhost:8090/servicio-pedidos/pedidos/1/estado?estado=CONFIRMADO"

# Eliminar pedido
curl -X DELETE http://localhost:8090/servicio-pedidos/pedidos/1
```

## Añadir una nueva ruta

Editar `config-repo/api-gateway/api-gateway.yml` y añadir una entrada en `spring.cloud.gateway.routes`:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: ruta-nuevo-servicio
              uri: lb://nombre-en-eureka
              predicates:
                - Path=/nuevo-servicio/**
              filters:
                - StripPrefix=1
```

El Config Server sirve el cambio sin reiniciar el gateway (siempre que esté levantado).

## Conceptos clave

### Predicados (`predicates`)

Condiciones que debe cumplir la petición para que se aplique la ruta. Además de `Path`, existen:

| Predicado | Ejemplo | Descripción |
|---|---|---|
| `Path` | `Path=/api/**` | Filtra por path |
| `Method` | `Method=GET,POST` | Filtra por método HTTP |
| `Header` | `Header=X-Token, abc` | Filtra por cabecera |
| `Query` | `Query=debug` | Filtra por parámetro de query |

### Filtros (`filters`)

Transformaciones que se aplican a la petición o respuesta. Los más comunes:

| Filtro | Descripción |
|---|---|
| `StripPrefix=N` | Elimina los N primeros segmentos del path |
| `AddRequestHeader=K,V` | Añade una cabecera a la petición |
| `AddResponseHeader=K,V` | Añade una cabecera a la respuesta |
| `RewritePath=/old,/new` | Reescribe el path con un patrón regex |
| `CircuitBreaker` | Aplica circuit breaker con Resilience4j |

### Esquema `lb://`

Indica al gateway que use el LoadBalancer de Spring Cloud para resolver la URI. El LoadBalancer consulta Eureka y elige una instancia disponible del servicio con ese nombre (balanceo round-robin por defecto).

## Tracing distribuido

El gateway genera un span por cada petición entrante y propaga el `traceId` al servicio destino mediante cabeceras HTTP (`b3` / `traceparent`). En Zipkin se ve la traza completa: un span del gateway y otro del servicio interno, todos bajo el mismo `traceId`.

```
Cliente → GET /servicio-pedidos/pedidos
  span: api-gateway          [traceId: abc123, spanId: 001]
    └─► span: servicio-pedidos [traceId: abc123, spanId: 002]
```

| Propiedad | Valor | Fuente |
|---|---|---|
| `management.tracing.sampling.probability` | `1.0` (100 % en desarrollo) | `config-repo/application.yml` |
| `management.zipkin.tracing.endpoint` | `http://localhost:9411/api/v2/spans` | `config-repo/application.yml` |

### Cómo ver las trazas en Zipkin

1. Abre http://localhost:9411
2. Pulsa **`+`** → elige **`serviceName`** → escribe `api-gateway`
3. Pulsa **Run Query**
4. Haz click en cualquier fila para ver el detalle del span (gateway + servicio destino bajo el mismo `traceId`)

## Actuator

| Endpoint | URL | Descripción |
|---|---|---|
| Health | `http://localhost:8090/actuator/health` | Estado del gateway |
| Gateway routes | `http://localhost:8090/actuator/gateway/routes` | Rutas activas con predicados y filtros |
| Gateway globalfilters | `http://localhost:8090/actuator/gateway/globalfilters` | Filtros globales aplicados a todas las rutas |

```bash
# Ver todas las rutas activas en tiempo de ejecución
curl http://localhost:8090/actuator/gateway/routes
```

## Tests

```bash
./gradlew :api-gateway:test
```

El test desactiva Config Server, Eureka y las rutas del gateway para que el contexto arranque sin infraestructura externa.
