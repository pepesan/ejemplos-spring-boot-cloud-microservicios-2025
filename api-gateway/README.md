# api-gateway

Punto de entrada único al ecosistema de microservicios. Recibe todas las peticiones externas y las enruta al servicio interno correspondiente, resolviendo la instancia destino mediante el registro de Eureka.

## Stack

| Tecnología | Detalle |
|---|---|
| Spring Cloud Gateway (WebFlux) | `spring-cloud-starter-gateway-server-webflux` |
| Spring Cloud Netflix Eureka Client | `spring-cloud-starter-netflix-eureka-client` |
| Spring Cloud Config Client | `spring-cloud-starter-config` |
| Spring Boot Actuator | `spring-boot-starter-actuator` |
| Puerto | `8090` |

> **Nota sobre el nombre del artefacto:** en Spring Cloud Gateway 5.x (Oakwood, 2025.1.1) el starter reactivo fue renombrado de `spring-cloud-starter-gateway` a `spring-cloud-starter-gateway-server-webflux`.

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
| `ruta-eureka-client` | `GET /eureka-client/**` | `lb://eureka-client` | `/**` (sin prefijo) |
| `ruta-config-client` | `GET /config-client/**` | `lb://config-client` | `/**` (sin prefijo) |

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
# A través del gateway (puerto 8080) — equivalente a llamar directamente a cada servicio
curl http://localhost:8090/eureka-client/
curl http://localhost:8090/eureka-client/hola
curl http://localhost:8090/eureka-client/servicios
curl http://localhost:8090/eureka-client/instancias

curl http://localhost:8090/config-client/config
```

## Añadir una nueva ruta

Editar `config-repo/api-gateway/api-gateway.yml` y añadir una entrada en `spring.cloud.gateway.routes`:

```yaml
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
