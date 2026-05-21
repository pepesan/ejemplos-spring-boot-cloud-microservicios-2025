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

## Mapa de puertos

### Infraestructura Docker (`docker/compose.yaml`)

| Servicio | Puerto host | Descripción | Arranque |
|----------|-------------|-------------|---------|
| Kafka broker | `9092` | Broker Kafka (acceso desde la máquina host) | `docker compose -f docker/compose.yaml up -d` |
| Kafka UI | `9001` | Consola web para inspeccionar topics y mensajes | `http://localhost:9001` |
| Zipkin | `9411` | UI de trazas distribuidas | `http://localhost:9411` |

### Microservicios Spring Boot

| Módulo | Puerto | Descripción |
|--------|--------|-------------|
| `eureka-server` | `8761` | Servidor de registro y descubrimiento · Dashboard: `http://localhost:8761` |
| `config-server` | `8888` | Servidor de configuración centralizada |
| `api-gateway` | `8090` | Punto de entrada único; enruta hacia los servicios internos |
| `eureka-client` | `8081` | Cliente de ejemplo registrado en Eureka |
| `config-client` | `8082` | Cliente de ejemplo del Config Server (perfiles desarrollo/produccion) |
| `servicio-productos` | `8083` | CRUD reactivo de productos; consume eventos `PedidoCreado` de Kafka |
| `servicio-pedidos` | `8084` | CRUD reactivo de pedidos; publica eventos `PedidoCreado` a Kafka |

## Requisitos previos

- JDK 25
- Gradle 9.5.1+ (o usar el wrapper `./gradlew`)
- Docker (para la infraestructura Kafka/Zipkin)

## Estructura del proyecto

```
ejemplos-spring-boot-cloud-microservicios/
├── build.gradle.kts        ← configuración común heredada por todos los módulos
├── settings.gradle.kts     ← registro de módulos
├── config-repo/            ← YAML centralizados, organizados en una subcarpeta por servicio
├── docker/                 ← compose.yaml con Kafka, Kafka UI y futura infraestructura
├── config-server/          ← servidor centralizado de configuración (Spring Cloud Config)
├── config-client/          ← cliente del Config Server con perfiles desarrollo/produccion
├── api-gateway/            ← punto de entrada único, enruta peticiones a los microservicios
├── eureka-server/          ← servidor de registro y descubrimiento
├── eureka-client/          ← microservicio cliente que se registra en Eureka
├── servicio-productos/     ← CRUD reactivo de productos + consumidor Kafka de PedidoCreado
└── servicio-pedidos/       ← CRUD reactivo de pedidos + WebClient + Circuit Breaker + StreamBridge
```

## Módulos

### servicio-pedidos

Microservicio reactivo que demuestra comunicación síncrona (WebClient + Circuit Breaker)
y asíncrona (StreamBridge → Kafka) dentro del mismo flujo de creación de un pedido.

- **Puerto:** `8084`
- **Base de datos:** H2 en memoria (R2DBC)
- **Topic Kafka publicado:** `pedidos-creados` → consumido por `servicio-productos`
- **Circuit Breaker:** Resilience4j protege la llamada a `servicio-productos`
- **Requiere:** `eureka-server`, `config-server`, Kafka y `servicio-productos` (opcional)

```bash
# Crear pedido (publica evento PedidoCreado automáticamente)
curl -X POST http://localhost:8084/pedidos \
  -H "Content-Type: application/json" \
  -d '{"productoId":1,"cantidad":2}'

# Actualizar estado del pedido
curl -X PATCH "http://localhost:8084/pedidos/1/estado?estado=CONFIRMADO"

# A través del API Gateway
curl -X POST http://localhost:8090/servicio-pedidos/pedidos \
  -H "Content-Type: application/json" \
  -d '{"productoId":1,"cantidad":2}'
```

---

### servicio-productos

Microservicio reactivo de gestión de catálogo de productos. Demuestra el uso de
Spring WebFlux + R2DBC para persistencia reactiva y Spring Cloud Stream para
consumir eventos Kafka.

- **Puerto:** `8083`
- **Base de datos:** H2 en memoria (R2DBC) — se puebla con `schema.sql` al arrancar
- **Topic Kafka escuchado:** `pedidos-creados`
- **Requiere:** `eureka-server`, `config-server` y Kafka arrancados

```bash
# CRUD directo
curl http://localhost:8083/productos
curl http://localhost:8083/productos/1
curl -X POST http://localhost:8083/productos \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Hub USB-C","descripcion":"7 puertos","precio":39.99,"stock":75}'

# A través del API Gateway
curl http://localhost:8090/servicio-productos/productos
```

Al recibir un evento `PedidoCreado` en Kafka, el stock del producto indicado se
decrementa automáticamente. El stock nunca baja de 0.

---

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
# 0. Infraestructura (Kafka, Kafka UI)
docker compose -f docker/compose.yaml up -d

# 1. Arrancar el servidor Eureka
./gradlew :eureka-server:bootRun

# 2. Arrancar el Config Server (en otra terminal)
./gradlew :config-server:bootRun

# 3. Arrancar el API Gateway (en otra terminal)
./gradlew :api-gateway:bootRun

# 4. Arrancar el config-client con el perfil deseado (en otra terminal)
./gradlew :config-client:bootRun --args='--spring.profiles.active=desarrollo'

# 5. Arrancar el eureka-client (en otra terminal)
./gradlew :eureka-client:bootRun

# 6. Arrancar el servicio de productos (en otra terminal)
./gradlew :servicio-productos:bootRun

# 7. Arrancar el servicio de pedidos (en otra terminal)
./gradlew :servicio-pedidos:bootRun

# 7. Verificar el registro en el dashboard de Eureka
# http://localhost:8761

# 8. Probar los endpoints
curl http://localhost:8082/config                           # config-client
curl http://localhost:8081/hola                            # eureka-client directo
curl http://localhost:8083/productos                       # servicio-productos directo
curl http://localhost:8090/servicio-productos/productos    # a través del gateway
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
./gradlew :servicio-productos:build
./gradlew :servicio-pedidos:build

# Arrancar un servicio
./gradlew :eureka-server:bootRun
./gradlew :config-server:bootRun
./gradlew :api-gateway:bootRun
./gradlew :eureka-client:bootRun
./gradlew :config-client:bootRun --args='--spring.profiles.active=desarrollo'
./gradlew :servicio-productos:bootRun
./gradlew :servicio-pedidos:bootRun

# Ejecutar tests de un módulo
./gradlew :eureka-server:test
./gradlew :eureka-client:test
./gradlew :config-server:test
./gradlew :config-client:test
./gradlew :api-gateway:test
./gradlew :servicio-productos:test
./gradlew :servicio-pedidos:test

# Ejecutar una clase de test concreta
./gradlew :eureka-client:test --tests "com.cursosdedesarrollo.eurekaclient.EurekaClientApplicationTest"
./gradlew :servicio-productos:test --tests "com.cursosdedesarrollo.servicioproductos.ServicioProductosApplicationTest"
./gradlew :servicio-pedidos:test --tests "com.cursosdedesarrollo.serviciopedidos.ServicioPedidosApplicationTest"

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

Sustituir el puerto por el del módulo correspondiente: `8761` (eureka-server), `8888` (config-server), `8090` (api-gateway), `8081` (eureka-client), `8082` (config-client), `8083` (servicio-productos), `8084` (servicio-pedidos).

## Convenciones de los módulos

- **groupId:** `com.cursosdedesarrollo`
- **Configuración:** exclusivamente en `application.yml`
- **Lombok:** disponible en todos los módulos sin declaración adicional
- **APIs reactivas:** se usa Spring WebFlux + Project Reactor (`Mono`/`Flux`) siempre que aplique

Cada módulo solo declara en su `build.gradle.kts` las dependencias específicas que añade; las versiones y dependencias comunes (Lombok, test) las gestiona el BOM del raíz.
