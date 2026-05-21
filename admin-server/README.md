# admin-server

Panel de monitorización para todos los microservicios del ecosistema, basado en **Spring Boot Admin 4.0.4**.

## Stack

| Tecnología | Versión |
|---|---|
| Spring Boot | 4.0.5 |
| Spring Boot Admin Server | 4.0.4 |
| Spring Security (WebFlux) | incluido en SB 4 |
| Eureka Client | Spring Cloud 2025.1.1 |
| Java | 25 |

## Puerto

`9090` → `http://localhost:9090`

## Credenciales por defecto

| Usuario | Contraseña |
|---|---|
| `admin` | `admin` |

## Cómo funciona

1. El `admin-server` se registra en **Eureka** como cualquier otro servicio.
2. También consume el registro de Eureka para descubrir automáticamente todos los servicios activos.
3. Cada servicio descubierto debe tener `spring-boot-starter-actuator` con los endpoints expuestos.
4. El panel muestra: estado de salud, métricas, variables de entorno, logs, threads, etc.

## Requisitos previos

- **Eureka Server** levantado en `localhost:8761` (módulo `eureka-server`).
- Los microservicios que se quieran monitorizar deben incluir en su `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"   # o la lista concreta de endpoints
  endpoint:
    health:
      show-details: always
```

## Arrancar

```bash
# Primero arranca Eureka
./gradlew :eureka-server:bootRun

# Luego el admin server
./gradlew :admin-server:bootRun
```

Panel disponible en: `http://localhost:9090`

## Endpoints actuator propios

| Endpoint | URL |
|---|---|
| Health | `http://localhost:9090/actuator/health` |
| Info | `http://localhost:9090/actuator/info` |

## Tests

```bash
./gradlew :admin-server:test
```

El test de contexto verifica que la aplicación arranca correctamente con seguridad y Eureka configurados.
