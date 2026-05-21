# config-client

Microservicio cliente del Config Server que demuestra cómo cargar configuración centralizada con soporte de perfiles (`desarrollo` / `produccion`). Expone un endpoint reactivo que muestra en tiempo de ejecución qué configuración está activa y desde qué perfil proviene.

## Stack

| Tecnología | Detalle |
|---|---|
| Spring Boot WebFlux | `spring-boot-starter-webflux` |
| Spring Cloud Config Client | `spring-cloud-starter-config` |
| Spring Cloud Netflix Eureka Client | `spring-cloud-starter-netflix-eureka-client` |
| Spring Boot Actuator | `spring-boot-starter-actuator` |
| Puerto | `8082` (recibido del Config Server) |

## Requisitos previos

Servicios que deben estar arrancados antes:

1. `eureka-server` en `localhost:8761`
2. `config-server` en `localhost:8888`

## Ficheros de configuración en `config-repo/`

El Config Server resuelve la configuración fusionando los ficheros en este orden de prioridad (mayor prioridad primero):

```
config-client-<perfil>.yml   ← propiedades específicas del perfil activo
config-client.yml            ← propiedades base del servicio (todos los perfiles)
application.yml              ← propiedades compartidas por todos los servicios
```

| Fichero | Perfil | `app.entorno` | `app.limitePeticiones` | Log level |
|---|---|---|---|---|
| `config-client.yml` | *(ninguno)* | `default` | 50 | — |
| `config-client-desarrollo.yml` | `desarrollo` | `desarrollo` | 10 | DEBUG |
| `config-client-produccion.yml` | `produccion` | `produccion` | 5000 | WARN |

## Arranque por perfil

### Sin perfil (configuración base)

```bash
./gradlew :config-client:bootRun
```

### Perfil `desarrollo`

```bash
./gradlew :config-client:bootRun --args='--spring.profiles.active=desarrollo'
```

O con variable de entorno:

```bash
SPRING_PROFILES_ACTIVE=desarrollo ./gradlew :config-client:bootRun
```

### Perfil `produccion`

```bash
./gradlew :config-client:bootRun --args='--spring.profiles.active=produccion'
```

O con variable de entorno:

```bash
SPRING_PROFILES_ACTIVE=produccion ./gradlew :config-client:bootRun
```

## Salida de log al arrancar

Al iniciarse, el componente `ConfigStartupLogger` imprime qué ficheros cargó el Config Server y los valores efectivos de `app.*`. Esto permite verificar de un vistazo qué perfil está activo y desde qué ficheros proviene cada propiedad.

### Sin perfil (configuración base)

```
╔══════════════════════════════════════════════════════╗
║           CONFIGURACIÓN CARGADA AL ARRANQUE          ║
╠══════════════════════════════════════════════════════╣
║ Perfiles activos : (ninguno — perfil por defecto)
╠══════════════════════════════════════════════════════╣
║ Ficheros cargados desde el Config Server (mayor prioridad primero):
║   → configserver:config-client.yml
║   → configserver:application.yml
╠══════════════════════════════════════════════════════╣
║ Valores efectivos de app.*:
║   app.entorno          = default
║   app.mensaje          = Configuración por defecto — no se ha activado ningún perfil
║   app.limitePeticiones = 50
╚══════════════════════════════════════════════════════╝
```

### Perfil `desarrollo`

```
╔══════════════════════════════════════════════════════╗
║           CONFIGURACIÓN CARGADA AL ARRANQUE          ║
╠══════════════════════════════════════════════════════╣
║ Perfiles activos : desarrollo
╠══════════════════════════════════════════════════════╣
║ Ficheros cargados desde el Config Server (mayor prioridad primero):
║   → configserver:config-client-desarrollo.yml
║   → configserver:config-client.yml
║   → configserver:application.yml
╠══════════════════════════════════════════════════════╣
║ Valores efectivos de app.*:
║   app.entorno          = desarrollo
║   app.mensaje          = Estás en el perfil de DESARROLLO — logs detallados, límite bajo
║   app.limitePeticiones = 10
╚══════════════════════════════════════════════════════╝
```

### Perfil `produccion`

```
╔══════════════════════════════════════════════════════╗
║           CONFIGURACIÓN CARGADA AL ARRANQUE          ║
╠══════════════════════════════════════════════════════╣
║ Perfiles activos : produccion
╠══════════════════════════════════════════════════════╣
║ Ficheros cargados desde el Config Server (mayor prioridad primero):
║   → configserver:config-client-produccion.yml
║   → configserver:config-client.yml
║   → configserver:application.yml
╠══════════════════════════════════════════════════════╣
║ Valores efectivos de app.*:
║   app.entorno          = produccion
║   app.mensaje          = Estás en el perfil de PRODUCCIÓN — logs mínimos, límite alto
║   app.limitePeticiones = 5000
╚══════════════════════════════════════════════════════╝
```

Los ficheros aparecen en orden de **mayor a menor prioridad**: el primero sobreescribe a los siguientes. Con perfil activo aparecen tres fuentes; sin perfil, solo dos.

### Sin Config Server disponible (`optional:`)

Si el `config-server` no está arrancado, el cliente inicia igualmente (por el prefijo `optional:` en `spring.config.import`) con los valores por defecto de `AppProperties`. El logger lo avisa explícitamente:

```
╠══════════════════════════════════════════════════════╣
║ Ficheros cargados desde el Config Server (mayor prioridad primero):
║   ⚠  Sin fuentes del Config Server — usando valores por defecto.
║      Comprueba que el config-server está arrancado en localhost:8888
║      y que spring.config.import apunta a la URL correcta.
╠══════════════════════════════════════════════════════╣
║ Valores efectivos de app.*:
║   app.entorno          = desconocido
║   app.mensaje          = sin configuración
║   app.limitePeticiones = 0
╚══════════════════════════════════════════════════════╝
```

> Para que el arranque falle si el Config Server no está disponible, eliminar el prefijo `optional:` en `spring.config.import` del `application.yml`.

## Verificar qué configuración está activa en tiempo de ejecución

```bash
curl http://localhost:8082/config
```

Respuesta de ejemplo con perfil `desarrollo`:

```json
{
  "entorno": "desarrollo",
  "mensaje": "Estás en el perfil de DESARROLLO — logs detallados, límite bajo",
  "limitePeticiones": 10,
  "perfilesActivos": ["desarrollo"]
}
```

## Consultar la configuración directamente en el Config Server

```bash
# Ver la config base (sin perfil)
curl http://localhost:8888/config-client/default

# Ver la config con perfil desarrollo
curl http://localhost:8888/config-client/desarrollo

# Ver la config con perfil produccion
curl http://localhost:8888/config-client/produccion

# Ver el YAML fusionado para un perfil concreto
curl http://localhost:8888/config-client-desarrollo.yml
curl http://localhost:8888/config-client-produccion.yml
```

## Cómo funciona `spring.config.import`

```yaml
# application.yml del cliente
spring:
  config:
    import: "optional:configserver:http://localhost:8888"
```

- El prefijo `optional:` permite que el servicio arranque aunque el Config Server no esté disponible (útil en desarrollo local).
- En producción se recomienda eliminar `optional:` para que un Config Server caído impida el arranque del servicio con configuración incorrecta o vacía.

## Endpoint

| Método | Ruta | Descripción | Tipo reactivo |
|---|---|---|---|
| `GET` | `/config` | Propiedades activas recibidas del Config Server | `Mono<Map>` |

## Actuator

| Endpoint | URL |
|---|---|
| Health | `http://localhost:8082/actuator/health` |
| Info | `http://localhost:8082/actuator/info` |
| Metrics | `http://localhost:8082/actuator/metrics` |
| Env | `http://localhost:8082/actuator/env` |

## Tests

```bash
./gradlew :config-client:test
```

El test inyecta las propiedades `app.*` directamente y desactiva el Config Server y Eureka, por lo que es autocontenido sin infraestructura externa.
