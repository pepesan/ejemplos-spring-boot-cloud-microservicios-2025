# Ejemplos Spring Boot Cloud Microservicios

Colección de ejemplos prácticos de microservicios con Spring Boot 4 y Spring Cloud. Cada módulo es un proyecto independiente que ilustra un componente o patrón concreto del ecosistema Spring Cloud.

## Stack tecnológico

| Tecnología | Versión |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.5 |
| Spring Cloud | 2025.1.1 (Oakwood) |
| Gradle (Kotlin DSL) | 9.5.1 |
| Lombok | gestionado por Spring Boot BOM |

## Requisitos previos

- JDK 25
- Gradle 9.5.1+ (o usar el wrapper `./gradlew`)

## Estructura del proyecto

```
ejemplos-spring-boot-cloud-microservicios/
├── build.gradle.kts        ← configuración común heredada por todos los módulos
├── settings.gradle.kts     ← registro de módulos
├── config-repo/            ← YAML centralizados, organizados en una subcarpeta por servicio
├── config-server/          ← servidor centralizado de configuración (Spring Cloud Config)
├── config-client/          ← cliente del Config Server con perfiles desarrollo/produccion
├── api-gateway/            ← punto de entrada único, enruta peticiones a los microservicios
├── eureka-server/          ← servidor de registro y descubrimiento
└── eureka-client/          ← microservicio cliente que se registra en Eureka
```

## Módulos

### config-server

Servidor centralizado de configuración basado en Spring Cloud Config Server. Lee los YAML del repositorio Git local `config-repo/` y los sirve a los microservicios clientes a través de una API REST.

- **Puerto:** `8888`
- **Backend:** modo `native` — lee los YAML de `config-repo/` directamente del sistema de ficheros, sin repositorio Git anidado (migrable a Git remoto en producción)
- **Dependencia clave:** `spring-cloud-config-server`

```bash
# Consultar la configuración servida para un servicio
curl http://localhost:8888/eureka-client/default
curl http://localhost:8888/eureka-client-default.yml
```

Los clientes se conectan añadiendo en su `application.yml`:
```yaml
spring:
  config:
    import: "optional:configserver:http://localhost:8888"
```

### api-gateway

Punto de entrada único al ecosistema. Recibe todas las peticiones externas y las enruta al microservicio interno correspondiente usando el registro de Eureka para resolver las instancias disponibles (balanceo de carga incluido).

- **Puerto:** `8090`
- **Dependencia clave:** `spring-cloud-starter-gateway-server-webflux` (renombrado en Gateway 5.x / Oakwood)
- **Rutas:** definidas en `config-repo/api-gateway/api-gateway.yml`

```bash
# Acceder a los servicios internos a través del gateway
curl http://localhost:8090/eureka-client/hola
curl http://localhost:8090/config-client/config
```

El filtro `StripPrefix=1` elimina el prefijo de la ruta antes de reenviar la petición al servicio destino (`/eureka-client/hola` → `/hola`).

### config-client

Microservicio que demuestra cómo un cliente consume configuración centralizada del Config Server con soporte de perfiles. Al arrancar con un perfil concreto, recibe el fichero `config-client-<perfil>.yml` fusionado con la configuración base.

- **Puerto:** `8082` (recibido del Config Server)
- **Requiere:** `eureka-server` en `localhost:8761` y `config-server` en `localhost:8888`

#### Perfiles disponibles

| Perfil | Fichero en `config-repo/` | `limitePeticiones` | Log level |
|---|---|---|---|
| *(ninguno)* | `config-client.yml` | 50 | — |
| `desarrollo` | `config-client-desarrollo.yml` | 10 | DEBUG |
| `produccion` | `config-client-produccion.yml` | 5000 | WARN |

#### Arrancar con un perfil

```bash
# Sin perfil (configuración base)
./gradlew :config-client:bootRun

# Perfil desarrollo
./gradlew :config-client:bootRun --args='--spring.profiles.active=desarrollo'

# Perfil produccion
./gradlew :config-client:bootRun --args='--spring.profiles.active=produccion'

# También con variable de entorno
SPRING_PROFILES_ACTIVE=produccion ./gradlew :config-client:bootRun
```

#### Verificar la configuración activa

```bash
# Ver qué configuración está cargada en el servicio arrancado
curl http://localhost:8082/config

# Consultar directamente en el Config Server (sin arrancar el cliente)
curl http://localhost:8888/config-client/desarrollo
curl http://localhost:8888/config-client/produccion
curl http://localhost:8888/config-client-produccion.yml
```

### eureka-server

Servidor de registro y descubrimiento basado en Netflix Eureka. Todos los microservicios del ecosistema se registran aquí y lo consultan para localizar otros servicios.

- **Puerto:** `8761`
- **Dashboard:** `http://localhost:8761`
- **Dependencia clave:** `spring-cloud-starter-netflix-eureka-server`

Para que un microservicio se registre en este servidor debe incluir `spring-cloud-starter-netflix-eureka-client` y apuntar su `eureka.client.service-url.defaultZone` a `http://localhost:8761/eureka/`.

> **Auto-preservación desactivada en desarrollo:** por defecto Eureka muestra el aviso `EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP` cuando recibe menos heartbeats de los esperados (umbral 85%). Con pocos servicios esto se dispara continuamente, por lo que en esta configuración está desactivado (`enable-self-preservation: false`). En producción con muchas instancias conviene reactivarlo.

### eureka-client

Microservicio de ejemplo que se registra automáticamente en el servidor Eureka al arrancar y expone endpoints reactivos (WebFlux) para demostrar el uso de `DiscoveryClient`.

- **Puerto:** `8081`
- **Dependencias clave:** `spring-cloud-starter-netflix-eureka-client`, `spring-boot-starter-webflux`
- **Requiere:** `eureka-server` arrancado en `localhost:8761`

| Endpoint | Descripción |
|---|---|
| `GET /` | Estado del servicio |
| `GET /hola` | Saludo simple para comprobar que el servicio está activo |
| `GET /servicios` | Lista los nombres de todos los servicios registrados en Eureka |
| `GET /instancias` | Muestra las instancias propias con su `serviceId` y URI |

> **Nota sobre `DiscoveryClient`:** es la abstracción genérica de Spring Cloud para el descubrimiento de servicios (funciona con Eureka, Consul, Zookeeper…). Sus llamadas son bloqueantes; para una integración completamente reactiva usar `ReactiveDiscoveryClient`.

## Orden de arranque recomendado

```bash
# 1. Arrancar el servidor Eureka
./gradlew :eureka-server:bootRun

# 2. Arrancar el Config Server (en otra terminal)
./gradlew :config-server:bootRun

# 3. Arrancar el config-client con el perfil deseado (en otra terminal)
./gradlew :config-client:bootRun --args='--spring.profiles.active=desarrollo'

# 4. Arrancar el eureka-client (en otra terminal)
./gradlew :eureka-client:bootRun

# 5. Verificar el registro en el dashboard de Eureka
# http://localhost:8761

# 6. Probar los endpoints
curl http://localhost:8082/config          # configuración activa del config-client
curl http://localhost:8081/hola
curl http://localhost:8081/servicios
curl http://localhost:8081/instancias
```

## Comandos Gradle

```bash
# Construir todos los módulos
./gradlew build

# Construir un módulo concreto
./gradlew :eureka-server:build
./gradlew :eureka-client:build
./gradlew :config-server:build
./gradlew :config-client:build
./gradlew :api-gateway:build

# Arrancar un servicio
./gradlew :eureka-server:bootRun
./gradlew :config-server:bootRun
./gradlew :api-gateway:bootRun
./gradlew :eureka-client:bootRun
./gradlew :config-client:bootRun --args='--spring.profiles.active=desarrollo'

# Ejecutar tests de un módulo
./gradlew :eureka-server:test
./gradlew :eureka-client:test
./gradlew :config-server:test
./gradlew :config-client:test
./gradlew :api-gateway:test

# Ejecutar una clase de test concreta
./gradlew :eureka-client:test --tests "com.cursosdedesarrollo.eurekaclient.EurekaClientApplicationTest"

# Construir sin tests
./gradlew build -x test
```

## Actuator

Todos los módulos exponen los siguientes endpoints de monitorización bajo `/actuator`:

| Endpoint | URL ejemplo | Descripción |
|---|---|---|
| `health` | `http://localhost:8761/actuator/health` | Estado del servicio con detalle completo |
| `info` | `http://localhost:8761/actuator/info` | Información de la aplicación |
| `metrics` | `http://localhost:8761/actuator/metrics` | Métricas de JVM y uso de recursos |
| `env` | `http://localhost:8761/actuator/env` | Propiedades del entorno activas |

Sustituir el puerto por el del módulo correspondiente: `8761` (eureka-server), `8888` (config-server), `8090` (api-gateway), `8081` (eureka-client), `8082` (config-client).

## Convenciones de los módulos

- **groupId:** `com.cursosdedesarrollo`
- **Configuración:** exclusivamente en `application.yml`
- **Lombok:** disponible en todos los módulos sin declaración adicional
- **APIs reactivas:** se usa Spring WebFlux + Project Reactor (`Mono`/`Flux`) siempre que aplique

Cada módulo solo declara en su `build.gradle.kts` las dependencias específicas que añade; las versiones y dependencias comunes (Lombok, test) las gestiona el BOM del raíz.
