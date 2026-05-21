# eureka-server

Servidor de registro y descubrimiento basado en Netflix Eureka. Actúa como registro central donde los microservicios clientes se dan de alta al arrancar y desde el que otros servicios consultan las instancias disponibles.

## Stack

| Tecnología | Detalle |
|---|---|
| Spring Cloud Netflix Eureka Server | `spring-cloud-starter-netflix-eureka-server` |
| Spring Boot Actuator | `spring-boot-starter-actuator` |
| Puerto | `8761` |

## Arranque

```bash
./gradlew :eureka-server:bootRun
```

Dashboard disponible en `http://localhost:8761` una vez arrancado.

## Configuración destacada

| Propiedad | Valor | Motivo |
|---|---|---|
| `register-with-eureka` | `false` | El servidor no se registra en sí mismo |
| `fetch-registry` | `false` | No necesita obtener la lista de servicios |
| `wait-time-in-ms-when-sync-empty` | `0` | Elimina la espera de réplicas en arranque local |
| `enable-self-preservation` | `false` | Evita el aviso EMERGENCY en desarrollo con pocos servicios |
| `renewal-percent-threshold` | `0.49` | Umbral reducido como red de seguridad |

> **Auto-preservación:** en producción con muchas instancias conviene reactivar `enable-self-preservation: true` (valor por defecto). Protege el registro ante pérdidas de red masivas evitando que Eureka expire instancias que siguen vivas.

## Actuator

| Endpoint | URL |
|---|---|
| Health | `http://localhost:8761/actuator/health` |
| Info | `http://localhost:8761/actuator/info` |
| Metrics | `http://localhost:8761/actuator/metrics` |
| Env | `http://localhost:8761/actuator/env` |

## Cómo registrar un cliente

Cualquier servicio que incluya `spring-cloud-starter-netflix-eureka-client` se registra automáticamente con esta configuración mínima:

```yaml
spring:
  application:
    name: nombre-del-servicio

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

## Tests

```bash
./gradlew :eureka-server:test
```

El test de contexto arranca el servidor completo. No requiere ningún cliente registrado para pasar.
