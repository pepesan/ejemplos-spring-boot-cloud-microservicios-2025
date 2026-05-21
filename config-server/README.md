# config-server

Servidor centralizado de configuración basado en Spring Cloud Config Server. Lee los ficheros YAML del directorio `config-repo/` (en la raíz del proyecto) y los sirve a los microservicios clientes a través de una API REST.

## Stack

| Tecnología | Detalle |
|---|---|
| Spring Cloud Config Server | `spring-cloud-config-server` |
| Spring Cloud Netflix Eureka Client | `spring-cloud-starter-netflix-eureka-client` |
| Spring Boot Actuator | `spring-boot-starter-actuator` |
| Puerto | `8888` |

## Backend de configuración: modo `native`

Por defecto el servidor usa el backend **`native`**, que lee los YAML directamente del sistema de ficheros sin necesitar un repositorio Git. Esto permite versionar los ficheros de configuración junto con el código fuente en el mismo repositorio del proyecto, sin problemas de repositorios anidados.

```
config-repo/
├── application.yml                        ← config compartida por TODOS los servicios
├── config-client/
│   ├── config-client.yml                  ← config base del config-client
│   ├── config-client-desarrollo.yml       ← config del config-client en desarrollo
│   └── config-client-produccion.yml       ← config del config-client en producción
├── eureka-client/
│   └── eureka-client.yml
└── eureka-server/
    └── eureka-server.yml
```

Cada servicio tiene su propia subcarpeta. El servidor busca en la raíz (`application.yml` compartido) y en la subcarpeta del servicio que hace la petición (`{application}/`).

**Regla de resolución:** Spring Cloud Config Server fusiona los ficheros en este orden de prioridad (mayor prioridad primero):

```
<servicio>-<perfil>.yml   ← propiedades del perfil activo
<servicio>.yml            ← propiedades base del servicio
application.yml           ← propiedades compartidas por todos los servicios
```

### Añadir configuración para un nuevo servicio

1. Crear `config-repo/<nombre-servicio>.yml` con las propiedades base.
2. Opcionalmente crear `config-repo/<nombre-servicio>-<perfil>.yml` por cada perfil.
3. El Config Server sirve los cambios en la siguiente petición sin necesidad de reiniciarse.

### Consultar la configuración servida

```bash
# Configuración fusionada de un servicio (perfil por defecto)
curl http://localhost:8888/config-client/default

# Configuración fusionada de un servicio con un perfil concreto
curl http://localhost:8888/config-client/desarrollo
curl http://localhost:8888/config-client/produccion

# YAML tal cual del fichero fusionado para un perfil
curl http://localhost:8888/config-client-desarrollo.yml
curl http://localhost:8888/config-client-produccion.yml
```

## Arranque

```bash
# 1. (Opcional) Arrancar Eureka para que el Config Server se registre
./gradlew :eureka-server:bootRun

# 2. Arrancar el Config Server
./gradlew :config-server:bootRun
```

## Cómo conectar un cliente

### 1. Añadir la dependencia en el cliente

```kotlin
// build.gradle.kts del servicio cliente
implementation("org.springframework.cloud:spring-cloud-starter-config")
```

### 2. Configurar la importación en `application.yml` del cliente

```yaml
spring:
  application:
    name: nombre-del-servicio   # debe coincidir con el nombre del fichero en config-repo
  config:
    import: "optional:configserver:http://localhost:8888"
```

El prefijo `optional:` hace que el servicio arranque igualmente si el Config Server no está disponible. Eliminarlo en producción para que un Config Server caído impida el arranque.

### Descubrimiento vía Eureka (alternativa a URL fija)

```yaml
spring:
  config:
    import: "optional:configserver:"
  cloud:
    config:
      discovery:
        enabled: true
        service-id: config-server
```

## Activar el backend Git (`application-git.yml`)

El módulo incluye el fichero `src/main/resources/application-git.yml` con la configuración completa del backend Git, listo para usar. Al activar el perfil `git`, este fichero sobreescribe el backend `native` del `application.yml`.

```bash
# Arrancar con backend Git
SPRING_PROFILES_ACTIVE=git ./gradlew :config-server:bootRun

# Con credenciales para repositorio privado
GIT_USERNAME=tu-usuario GIT_TOKEN=ghp_xxx \
  SPRING_PROFILES_ACTIVE=git ./gradlew :config-server:bootRun
```

Propiedades clave documentadas en ese fichero:

| Propiedad | Ejemplo | Descripción |
|---|---|---|
| `uri` | `https://github.com/usuario/repo` | URL del repositorio remoto |
| `search-paths` | `config/servicios` | Carpeta dentro del repo con los YAML |
| `search-paths` | `config/{application}` | Carpeta dinámica por nombre de servicio |
| `default-label` | `main` | Rama por defecto |
| `clone-on-start` | `true` | Clona al arrancar para detectar errores pronto |
| `refresh-rate` | `30` | Segundos entre pulls al repositorio remoto |

## Migrar a backend Git (producción)

Para entornos de producción se recomienda usar un repositorio Git remoto como backend. Los cambios de configuración quedan auditados en el historial y el servidor puede detectarlos automáticamente.

En `application.yml` del config-server:

1. Eliminar o comentar el bloque `spring.profiles.active: native` y `spring.cloud.config.server.native`.
2. Descomentar el bloque `spring.cloud.config.server.git`:

```yaml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/tu-usuario/config-repo
          default-label: main
          clone-on-start: true
          # Si el repositorio es privado:
          username: ${GIT_USERNAME}
          password: ${GIT_TOKEN}   # Personal Access Token, no la contraseña
```

Las credenciales nunca deben escribirse en el YAML directamente. Pasarlas como variables de entorno:

```bash
GIT_USERNAME=tu-usuario GIT_TOKEN=ghp_xxx ./gradlew :config-server:bootRun
```

## Configuración destacada

| Propiedad | Valor | Motivo |
|---|---|---|
| `spring.profiles.active` | `native` | Lee YAML desde el sistema de ficheros; evita repositorio Git anidado dentro del proyecto |
| `search-locations` | `file://${user.dir}/config-repo` | Directorio de configuraciones en la raíz del proyecto |

### Por qué `${user.dir}` y no una ruta absoluta

`${user.dir}` es la propiedad de sistema Java que contiene el directorio de trabajo del proceso. Usar una ruta relativa a ella en lugar de una ruta absoluta hace que la configuración sea portátil entre máquinas.

**Problema con Gradle:** por defecto, `bootRun` usa el directorio del submódulo (`config-server/`) como working directory, por lo que `${user.dir}` resolvería a `.../config-server/` en vez de a la raíz del proyecto — y `config-repo/` no se encontraría.

**Solución:** en el `build.gradle.kts` raíz se fija el working directory de todos los `bootRun` a la raíz del proyecto:

```kotlin
tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    workingDir = rootProject.projectDir
}
```

Esto aplica a todos los módulos del proyecto.

## Actuator

| Endpoint | URL |
|---|---|
| Health | `http://localhost:8888/actuator/health` |
| Info | `http://localhost:8888/actuator/info` |
| Metrics | `http://localhost:8888/actuator/metrics` |
| Env | `http://localhost:8888/actuator/env` |

## Tests

```bash
./gradlew :config-server:test
```

El test desactiva Eureka y el perfil `native` ya está activo por defecto, por lo que es autocontenido sin infraestructura externa.
