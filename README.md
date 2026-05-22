# Ejemplos Spring Boot Cloud Microservicios

Colección de ejemplos prácticos de microservicios con Spring Boot 4 y Spring Cloud. Cada módulo ilustra un componente o patrón concreto del ecosistema Spring Cloud.

---

## Dos modos de descubrimiento de servicios

Este repositorio contiene **dos stacks independientes** que demuestran el mismo patrón de descubrimiento con tecnologías distintas. No es necesario levantar ambos a la vez.

| | Eureka | Consul |
|---|---|---|
| **Registry** | `eureka-server` (puerto 8761, JVM) | `consul` Docker (puerto 8500, agente externo) |
| **Módulos implicados** | `eureka-client`, `config-client`, `api-gateway`, `servicio-productos`, `servicio-pedidos`, `admin-server` | `consul-client` |
| **Configuración centralizada** | `config-server` (backend nativo sobre `config-repo/`) | cada servicio lleva su propio `application.yml` |
| **Health check** | heartbeat periódico hacia Eureka | HTTP poll de Consul a `/actuator/health` |
| **UI de registro** | http://localhost:8761 | http://localhost:8500/ui |
| **Infraestructura Docker adicional** | Kafka, Kafka UI, Zipkin | solo Consul (+ Zipkin opcional para trazas) |

> El stack Eureka está documentado en [Orden de arranque](#orden-de-arranque).
> El stack Consul está documentado en [Sistema con Consul — arranque completo](#sistema-con-consul--arranque-completo).

---

## Stack tecnológico

| Tecnología              | Versión                  |
|-------------------------|--------------------------|
| Java                    | 25                       |
| Spring Boot             | 4.0.5                    |
| Spring Cloud            | 2025.1.1 (Oakwood)       |
| Spring Boot Admin       | 4.0.4                    |
| Micrometer Tracing      | 1.6.4 (gestionado por SB BOM) |
| Zipkin                  | vía `spring-boot-starter-zipkin` |
| Gradle (Kotlin DSL)     | 9.5.1                    |
| Lombok                  | gestionado por SB BOM    |

---

## Mapa de puertos

### Infraestructura (Docker)

| Servicio    | Puerto  | URL                      | Descripción                              |
|-------------|---------|--------------------------|------------------------------------------|
| Kafka       | `9092`  | —                        | Broker Kafka KRaft (acceso desde host)   |
| Kafka UI    | `9001`  | http://localhost:9001    | Consola web de topics y mensajes         |
| Zipkin      | `9411`  | http://localhost:9411    | UI de trazas distribuidas                |
| Consul      | `8500`  | http://localhost:8500    | Service mesh: registro, KV y UI          |

### Microservicios

| Módulo               | Puerto  | URL                       | Descripción                                       |
|----------------------|---------|---------------------------|---------------------------------------------------|
| `eureka-server`      | `8761`  | http://localhost:8761     | Registro y descubrimiento de servicios            |
| `config-server`      | `8888`  | http://localhost:8888     | Configuración centralizada (backend nativo)       |
| `eureka-client`      | `8081`  | http://localhost:8081     | Cliente Eureka de ejemplo con endpoints reactivos |
| `config-client`      | `8082`  | http://localhost:8082     | Cliente Config Server con perfiles dev/prod       |
| `api-gateway`        | `8090`  | http://localhost:8090     | Punto de entrada único; enruta a todos los demás  |
| `servicio-productos` | `8083`  | http://localhost:8083     | CRUD reactivo de productos + consumidor Kafka     |
| `servicio-pedidos`   | `8084`  | http://localhost:8084     | CRUD reactivo de pedidos + Circuit Breaker + Kafka|
| `admin-server`       | `9090`  | http://localhost:9090     | Panel Spring Boot Admin (admin / admin)           |
| `consul-client`      | `8085`  | http://localhost:8085     | Cliente Consul: descubrimiento, config KV por perfil, CRUD Tareas R2DBC |

---

## Requisitos previos

- JDK 25
- Gradle 9.5.1+ (o el wrapper `./gradlew` incluido en el repo)
- Docker y Docker Compose (para Kafka, Kafka UI y Zipkin)

---

## Orden de arranque

Los servicios deben arrancarse **en este orden exacto**. Cada uno depende de los anteriores.

### Paso 0 — Infraestructura Docker

```bash
docker compose -f docker/compose.yaml up -d
```

Levanta Kafka (9092), Kafka UI (9001), Zipkin (9411) y Consul (8500). Esperar a que los contenedores estén `healthy` antes de continuar.

| Servicio  | URL de verificación           |
|-----------|-------------------------------|
| Kafka UI  | http://localhost:9001         |
| Zipkin    | http://localhost:9411         |
| Consul UI | http://localhost:8500/ui      |

---

### Paso 1 — eureka-server `→ :8761`

Servidor de registro. Todos los demás servicios se registran aquí.

```bash
./gradlew :eureka-server:bootRun
```

Verificar: http://localhost:8761

---

### Paso 2 — config-server `→ :8888`

Sirve los YAML de `config-repo/` a todos los clientes.

```bash
./gradlew :config-server:bootRun
```

Verificar:
```bash
curl http://localhost:8888/eureka-client/default
curl http://localhost:8888/servicio-productos/default
```

---

### Paso 3 — eureka-client `→ :8081`

Microservicio cliente de ejemplo registrado en Eureka.

```bash
./gradlew :eureka-client:bootRun
```

| Endpoint           | Descripción                                        |
|--------------------|----------------------------------------------------|
| `GET /hola`        | Saludo simple                                      |
| `GET /servicios`   | Servicios registrados en Eureka                    |
| `GET /instancias`  | Instancias propias con `serviceId` y URI           |

```bash
curl http://localhost:8081/hola
curl http://localhost:8081/servicios
```

---

### Paso 4 — config-client `→ :8082`

Demuestra el consumo del Config Server con soporte de perfiles.

```bash
# Perfil por defecto
./gradlew :config-client:bootRun

# Perfil desarrollo (limitePeticiones=10, log DEBUG)
./gradlew :config-client:bootRun --args='--spring.profiles.active=desarrollo'

# Perfil produccion (limitePeticiones=5000, log WARN)
./gradlew :config-client:bootRun --args='--spring.profiles.active=produccion'
```

| Perfil       | `limitePeticiones` | Log level |
|--------------|--------------------|-----------|
| *(ninguno)*  | 50                 | —         |
| `desarrollo` | 10                 | DEBUG     |
| `produccion` | 5000               | WARN      |

```bash
curl http://localhost:8082/config
```

---

### Paso 5 — api-gateway `→ :8090`

Punto de entrada único. Enruta peticiones a los servicios internos usando Eureka para el balanceo de carga.

```bash
./gradlew :api-gateway:bootRun
```

Las rutas están definidas en `config-repo/api-gateway/api-gateway.yml`. El filtro `StripPrefix=1` elimina el prefijo de ruta antes de reenviar (`/servicio-productos/productos` → `/productos`).

```bash
curl http://localhost:8090/eureka-client/hola
curl http://localhost:8090/config-client/config
curl http://localhost:8090/servicio-productos/productos
curl http://localhost:8090/servicio-pedidos/pedidos
```

---

### Paso 6 — servicio-productos `→ :8083`

CRUD reactivo de productos con R2DBC + H2 en memoria. Consume eventos `PedidoCreado` de Kafka y decrementa stock automáticamente.

```bash
./gradlew :servicio-productos:bootRun
```

| Endpoint                    | Método   | Descripción                          |
|-----------------------------|----------|--------------------------------------|
| `/productos`                | `GET`    | Listar todos los productos           |
| `/productos/{id}`           | `GET`    | Obtener producto por ID              |
| `/productos`                | `POST`   | Crear producto                       |
| `/productos/{id}`           | `PUT`    | Actualizar producto                  |
| `/productos/{id}`           | `DELETE` | Eliminar producto                    |

```bash
# Listar productos
curl http://localhost:8083/productos

# Crear producto
curl -X POST http://localhost:8083/productos \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Hub USB-C","descripcion":"7 puertos","precio":39.99,"stock":75}'

# Via gateway
curl http://localhost:8090/servicio-productos/productos
```

---

### Paso 7 — servicio-pedidos `→ :8084`

CRUD reactivo de pedidos. Al crear un pedido: llama a `servicio-productos` vía WebClient (protegido con Circuit Breaker Resilience4j) y publica el evento `PedidoCreado` en Kafka.

```bash
./gradlew :servicio-pedidos:bootRun
```

| Endpoint                          | Método   | Descripción                              |
|-----------------------------------|----------|------------------------------------------|
| `/pedidos`                        | `GET`    | Listar todos los pedidos                 |
| `/pedidos/{id}`                   | `GET`    | Obtener pedido por ID                    |
| `/pedidos`                        | `POST`   | Crear pedido (dispara evento Kafka)      |
| `/pedidos/{id}/estado?estado=X`   | `PATCH`  | Actualizar estado del pedido             |

```bash
# Crear pedido (publica evento → servicio-productos decrementa stock)
curl -X POST http://localhost:8084/pedidos \
  -H "Content-Type: application/json" \
  -d '{"productoId":1,"cantidad":2}'

# Actualizar estado
curl -X PATCH "http://localhost:8084/pedidos/1/estado?estado=CONFIRMADO"

# Via gateway
curl -X POST http://localhost:8090/servicio-pedidos/pedidos \
  -H "Content-Type: application/json" \
  -d '{"productoId":1,"cantidad":2}'
```

El circuit breaker abre si `servicio-productos` no está disponible; el pedido se crea igualmente.

---

### Paso 8 — admin-server `→ :9090`

Panel Spring Boot Admin. Descubre automáticamente todos los servicios registrados en Eureka y muestra salud, métricas, logs, variables de entorno y threads de cada uno.

```bash
./gradlew :admin-server:bootRun
```

Panel: http://localhost:9090 — usuario: `admin` / contraseña: `admin`

---

## Tracing distribuido (Micrometer Tracing + Zipkin)

Todos los servicios de negocio (`eureka-client`, `config-client`, `api-gateway`, `servicio-productos`, `servicio-pedidos`) están instrumentados con **Micrometer Tracing + Brave** y envían sus trazas a Zipkin.

### Cómo funciona

Cada petición HTTP entrante genera un **span** con un `traceId` único. Ese `traceId` se propaga automáticamente:
- A servicios downstream mediante **cabeceras HTTP** (`b3` / `traceparent`)
- A consumidores Kafka mediante **cabeceras del mensaje** Kafka

Esto permite ver en Zipkin la cadena completa de una operación, aunque involucre varios servicios y mensajes asíncronos.

### Traza más completa: crear un pedido

Hacer un `POST /pedidos` genera una única traza con **5 spans** enlazados:

```
[RAIZ] [servicio-pedidos]  http post /pedidos          (~640 ms)
  [hijo] [servicio-pedidos]  http get                    (~113 ms)  ← WebClient saliente
  [hijo] [servicio-productos] http get /productos/{id}   (~2 ms)    ← servidor productos
  [hijo] [servicio-pedidos]  streambridge process        (~2 ms)    ← preparación Kafka
  [hijo] [servicio-pedidos]  pedidos-creados-out-0 send  (~11 ms)   ← envío al topic
```

Todos los spans comparten el mismo `traceId`. Zipkin muestra la cascada de tiempos y las dependencias entre servicios.

### Cómo ver las trazas en Zipkin

La UI de Zipkin usa un sistema de filtros que se añaden uno a uno con el botón `+`.

#### Buscar trazas de un servicio concreto

1. Abre http://localhost:9411
2. Pulsa **`+`** → elige **`serviceName`** → escribe el nombre del servicio (p. ej. `servicio-pedidos`)
3. Pulsa **Run Query**
4. Haz click en cualquier fila para ver el detalle del span

#### Buscar la traza completa de 5 spans (crear pedido)

La traza más completa es la del `POST /pedidos`. Para encontrarla sin que quede tapada por las llamadas al actuator (que generan trazas de 1 span cada 30 s):

1. Abre http://localhost:9411
2. Pulsa **`+`** → elige **`serviceName`** → escribe `servicio-pedidos`
3. Pulsa **`+`** de nuevo → elige **`spanName`** → escribe `http post /pedidos`
4. Pulsa **Run Query**
5. Haz click en la entrada que muestre **5 spans**

```bash
# Primero genera un pedido para tener una traza reciente
curl -X POST http://localhost:8084/pedidos \
  -H "Content-Type: application/json" \
  -d '{"productoId":1,"cantidad":2}'
```

### Configuración

La config de tracing es compartida para todos los servicios con Config Server mediante `config-repo/application.yml`. El `eureka-client` (sin Config Server) la tiene en su `application.yml` local.

```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # 100 % en desarrollo; bajar a 0.1 en producción
    export:
      zipkin:
        endpoint: http://localhost:9411/api/v2/spans
```

### Notas importantes sobre Spring Boot 4

#### Starter correcto

En Spring Boot 4 la auto-configuración de Zipkin se movió a módulos propios. Usar:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-zipkin")
```

Añadir `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` directamente **no activa la auto-configuración** y no genera trazas.

#### Propagación de contexto en código reactivo

En Spring Boot 4 con WebFlux + Reactor, el contexto de traza no se propaga automáticamente a código imperativo ni a `WebClient` creado con `@LoadBalanced`. Se necesitan dos ajustes en `servicio-pedidos` (el único servicio que mezcla reactivo con StreamBridge y WebClient con load balancer):

**1. Activar propagación Reactor → ThreadLocal** (en la clase `main`):

```java
Hooks.enableAutomaticContextPropagation(); // antes de SpringApplication.run()
```

Sin esto, `StreamBridge.send()` ejecutado en un callback reactivo (`doOnSuccess`) no hereda el `traceId` del span padre y genera una traza nueva desconectada.

**2. Registrar el `ObservationRegistry` en el WebClient** (en `WebClientConfig`):

```java
@Bean @LoadBalanced
public WebClient.Builder loadBalancedWebClientBuilder(ObservationRegistry registry) {
    return WebClient.builder().observationRegistry(registry);
}
```

Sin esto, el `WebClient.Builder` creado manualmente no hereda los filtros de tracing que Spring Boot aplica solo al builder auto-configurado, y cada llamada saliente inicia un `traceId` nuevo.

---

## Sistema con Consul — arranque completo

> Stack **independiente** del de Eureka. No es necesario tener `eureka-server`, `config-server` ni ningún otro microservicio del stack Eureka activo.

### Componentes involucrados

#### Infraestructura Docker

| Servicio | Puerto | URL                   | Rol                                                       |
|----------|--------|-----------------------|-----------------------------------------------------------|
| Consul   | `8500` | http://localhost:8500 | Agente servidor: registry, health-checks, KV, UI          |
| Zipkin   | `9411` | http://localhost:9411 | Trazas distribuidas (opcional pero recomendado)           |

#### Microservicios

| Módulo          | Puerto | URL                   | Descripción                                                                          |
|-----------------|--------|-----------------------|--------------------------------------------------------------------------------------|
| `consul-client` | `8085` | http://localhost:8085 | Lee configuración del KV de Consul al arrancar; se registra para descubrimiento     |

### Paso 1 — Infraestructura Docker

Solo son necesarios Consul y (opcionalmente) Zipkin. Kafka no se usa en este stack.

```bash
docker compose -f docker/compose.yaml up -d consul zipkin
```

Esperar a que Consul esté `healthy`:

```bash
docker compose -f docker/compose.yaml ps consul
```

Verificar la UI de Consul: http://localhost:8500/ui

---

### Paso 2 — Cargar configuración en Consul KV (opcional)

Antes de arrancar el microservicio se puede pre-cargar su configuración en el KV store. Si no existe, el servicio arrancará con los valores por defecto.

La ruta del KV es `config/consul-client/data`. El valor debe ser YAML:

```yaml
consulclient:
  mensaje: "Hola desde Consul KV!"
  limite: 42
  entorno: "consul"
```

**Vía UI:**

1. Abre http://localhost:8500/ui → **Key/Value**
2. Crea la clave `config/consul-client/data` y pega el YAML anterior
3. Guarda

**Vía CLI (Docker):**

```bash
docker exec consul consul kv put config/consul-client/data \
'consulclient:
  mensaje: "Hola desde Consul KV!"
  limite: 42
  entorno: "consul"'
```

---

### Paso 3 — consul-client `→ :8085`

```bash
./gradlew :consul-client:bootRun
```

Al arrancar, el servicio:

1. Lee la configuración de `config/consul-client/data` en el KV de Consul.
2. Se registra con el nombre `consul-client` y su IP + puerto.
3. Declara un health-check HTTP: Consul llama a `/actuator/health` cada 10 s.
4. Activa un watcher sobre el KV: si cambia el valor, los beans `@RefreshScope` se recargan sin reiniciar.

Verificar el registro en la UI:

```
http://localhost:8500/ui → Services → consul-client → estado: passing
```

---

### Verificación de endpoints

```bash
# Saludo simple
curl http://localhost:8085/hola

# Configuración activa leída desde Consul KV
curl http://localhost:8085/config

# Servicios registrados en Consul en este momento
curl http://localhost:8085/servicios

# Instancias del propio consul-client
curl http://localhost:8085/instancias
```

Respuesta esperada de `/config` con el KV cargado en el paso 2:

```json
{"mensaje":"Hola desde Consul KV!","limite":42,"entorno":"consul"}
```

Sin KV (valores por defecto):

```json
{"mensaje":"configuración local (Consul KV no disponible)","limite":100,"entorno":"local"}
```

Ejemplo de respuesta de `/servicios` con solo `consul-client` registrado:

```json
["consul","consul-client"]
```

> `consul` aparece siempre: es el propio agente, que se auto-registra.

---

### Recarga en caliente de configuración

El watcher detecta cambios en el KV. Para probar la recarga sin reiniciar:

```bash
# 1. Modificar el valor en Consul KV
docker exec consul consul kv put config/consul-client/data \
'consulclient:
  mensaje: "Configuración actualizada en caliente!"
  limite: 999
  entorno: "produccion"'

# 2. Sin reiniciar el servicio, llamar al endpoint
curl http://localhost:8085/config
# → {"mensaje":"Configuración actualizada en caliente!","limite":999,"entorno":"produccion"}
```

---

### Qué observar en la UI de Consul

| Pestaña | Qué muestra |
|---------|-------------|
| **Services** | Todos los servicios registrados con su estado (`passing` / `critical`) |
| **Services → consul-client → Health Checks** | Resultado del último poll a `/actuator/health` |
| **Nodes** | El nodo del agente servidor con sus checks de sistema |
| **Key/Value → config/consul-client/data** | YAML de configuración activo para este servicio |

---

### Comportamiento del health-check

Consul llama a `http://<ip>:8085/actuator/health` cada 10 s.

| Estado Actuator | Estado en Consul | Efecto                                                  |
|-----------------|------------------|---------------------------------------------------------|
| `UP`            | `passing`        | La instancia aparece en las respuestas de descubrimiento |
| `DOWN`          | `critical`       | La instancia se excluye del descubrimiento              |

Para simular un fallo: detener el servicio y observar cómo Consul lo marca `critical` en la UI pasados ~10 s.

---

### Comparativa rápida con Eureka

| Aspecto | Eureka | Consul |
|---------|--------|--------|
| Proceso registry | JVM propia (`eureka-server`) | Agente externo (Docker) |
| Health check | heartbeat activo del cliente | poll HTTP pasivo desde el servidor |
| Tiempo de detección de caída | `leaseExpirationDuration` (30 s) | `health-check-interval` (10 s) |
| KV store | No | Sí (base para config centralizada) |
| DNS integrado | No | Sí (puerto 8600/udp) |
| UI | Panel Eureka (/eureka/apps) | UI completa en /ui |

---

## Comandos Gradle

```bash
# Construir todos los módulos (sin tests)
./gradlew build -x test

# Construir un módulo concreto
./gradlew :eureka-server:build
./gradlew :config-server:build
./gradlew :eureka-client:build
./gradlew :config-client:build
./gradlew :api-gateway:build
./gradlew :servicio-productos:build
./gradlew :servicio-pedidos:build
./gradlew :admin-server:build
./gradlew :consul-client:build

# Tests de todos los módulos
./gradlew test

# Tests de un módulo concreto
./gradlew :eureka-server:test
./gradlew :config-server:test
./gradlew :eureka-client:test
./gradlew :config-client:test
./gradlew :api-gateway:test
./gradlew :servicio-productos:test
./gradlew :servicio-pedidos:test
./gradlew :admin-server:test
./gradlew :consul-client:test
```

---

## Endpoints Actuator

Todos los servicios exponen `/actuator` con los siguientes endpoints:

| Endpoint    | Descripción                            |
|-------------|----------------------------------------|
| `/health`   | Estado del servicio con detalle        |
| `/info`     | Información de la aplicación           |
| `/metrics`  | Métricas de JVM y recursos             |
| `/env`      | Propiedades del entorno activas        |

Ejemplo: `http://localhost:{puerto}/actuator/health`

| Servicio             | URL Actuator health                             |
|----------------------|-------------------------------------------------|
| `eureka-server`      | http://localhost:8761/actuator/health           |
| `config-server`      | http://localhost:8888/actuator/health           |
| `eureka-client`      | http://localhost:8081/actuator/health           |
| `config-client`      | http://localhost:8082/actuator/health           |
| `api-gateway`        | http://localhost:8090/actuator/health           |
| `servicio-productos` | http://localhost:8083/actuator/health           |
| `servicio-pedidos`   | http://localhost:8084/actuator/health           |
| `admin-server`       | http://localhost:9090/actuator/health           |
| `consul-client`      | http://localhost:8085/actuator/health           |

---

## Estructura del proyecto

```
ejemplos-spring-boot-cloud-microservicios/
├── build.gradle.kts        ← configuración común (Spring Boot BOM, Lombok, Java 25)
├── settings.gradle.kts     ← registro de todos los módulos
├── config-repo/            ← YAMLs centralizados por servicio (backend del config-server)
├── docker/
│   └── compose.yaml        ← Kafka KRaft, Kafka UI, Zipkin y Consul
├── eureka-server/          ← servidor de registro y descubrimiento (puerto 8761)
├── config-server/          ← servidor de configuración centralizada (puerto 8888)
├── eureka-client/          ← cliente Eureka de ejemplo (puerto 8081)
├── config-client/          ← cliente Config Server con perfiles (puerto 8082)
├── api-gateway/            ← punto de entrada único con enrutamiento dinámico (puerto 8090)
├── servicio-productos/     ← CRUD reactivo + consumidor Kafka PedidoCreado (puerto 8083)
├── servicio-pedidos/       ← CRUD reactivo + Circuit Breaker + productor Kafka (puerto 8084)
├── admin-server/           ← Spring Boot Admin 4.0.4, monitorización del ecosistema (puerto 9090)
└── consul-client/          ← cliente Consul: registro y descubrimiento de servicios (puerto 8085)
```

---

## Convenciones

- **groupId:** `com.cursosdedesarrollo`
- **Configuración:** exclusivamente en `application.yml`, nunca `.properties`
- **Lombok:** disponible en todos los módulos sin declaración adicional
- **APIs reactivas:** Spring WebFlux + Project Reactor (`Mono`/`Flux`) siempre que aplique
- **Dependencias:** cada módulo declara solo las suyas; versiones gestionadas por el BOM raíz
