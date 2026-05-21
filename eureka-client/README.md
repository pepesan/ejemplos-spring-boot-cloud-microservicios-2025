# eureka-client

Microservicio de ejemplo que demuestra el registro automático en Eureka y el uso de `DiscoveryClient` para consultar el registro en tiempo de ejecución. Expone endpoints reactivos con Spring WebFlux.

## Stack

| Tecnología | Detalle |
|---|---|
| Spring Boot WebFlux | `spring-boot-starter-webflux` |
| Spring Cloud Netflix Eureka Client | `spring-cloud-starter-netflix-eureka-client` |
| Spring Boot Actuator | `spring-boot-starter-actuator` |
| Puerto | `8081` |

## Requisitos previos

`eureka-server` arrancado en `localhost:8761`.

## Arranque

```bash
# 1. Arrancar primero el servidor Eureka
./gradlew :eureka-server:bootRun

# 2. Arrancar este cliente
./gradlew :eureka-client:bootRun
```

## Endpoints

| Método | Ruta | Descripción | Tipo reactivo |
|---|---|---|---|
| `GET` | `/` | Estado del servicio | `Mono<String>` |
| `GET` | `/hola` | Saludo simple | `Mono<String>` |
| `GET` | `/servicios` | Nombres de todos los servicios registrados en Eureka | `Flux<String>` |
| `GET` | `/instancias` | Instancias propias con `serviceId` y URI | `Flux<String>` |

```bash
curl http://localhost:8081/
curl http://localhost:8081/hola
curl http://localhost:8081/servicios
curl http://localhost:8081/instancias
```

## Configuración destacada

| Propiedad | Valor | Motivo |
|---|---|---|
| `prefer-ip-address` | `true` | Registra la IP en lugar del hostname; recomendado en Docker o redes donde el hostname no es resoluble |
| `lease-renewal-interval-in-seconds` | `10` | Heartbeat cada 10 s (por defecto 30 s) para reflejar cambios más rápido en desarrollo |
| `lease-expiration-duration-in-seconds` | `30` | Eureka expira la instancia a los 30 s sin heartbeat (ajustado al intervalo reducido) |

## Actuator

| Endpoint | URL |
|---|---|
| Health | `http://localhost:8081/actuator/health` |
| Info | `http://localhost:8081/actuator/info` |
| Metrics | `http://localhost:8081/actuator/metrics` |
| Env | `http://localhost:8081/actuator/env` |

## Nota sobre DiscoveryClient

`DiscoveryClient` es la abstracción genérica de Spring Cloud para el descubrimiento de servicios (compatible con Eureka, Consul, Zookeeper…). Sus llamadas son **bloqueantes**; se envuelven en `Flux.fromIterable` para respetar la API reactiva del controlador. Para una integración completamente no-bloqueante usar `ReactiveDiscoveryClient`.

## Tests

```bash
./gradlew :eureka-client:test
```

El test desactiva el registro en Eureka (`eureka.client.enabled=false`) para que el contexto arranque sin necesitar el servidor disponible.
