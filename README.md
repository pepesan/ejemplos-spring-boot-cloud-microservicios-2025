# Ejemplos Spring Boot Cloud Microservicios

Colección de ejemplos prácticos de microservicios con Spring Boot 4 y Spring Cloud. Cada módulo ilustra un componente o patrón concreto del ecosistema Spring Cloud.

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

Levanta Kafka (9092), Kafka UI (9001) y Zipkin (9411). Esperar a que los contenedores estén `healthy` antes de continuar.

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
  zipkin:
    tracing:
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

---

## Estructura del proyecto

```
ejemplos-spring-boot-cloud-microservicios/
├── build.gradle.kts        ← configuración común (Spring Boot BOM, Lombok, Java 25)
├── settings.gradle.kts     ← registro de todos los módulos
├── config-repo/            ← YAMLs centralizados por servicio (backend del config-server)
├── docker/
│   └── compose.yaml        ← Kafka KRaft, Kafka UI y Zipkin
├── eureka-server/          ← servidor de registro y descubrimiento (puerto 8761)
├── config-server/          ← servidor de configuración centralizada (puerto 8888)
├── eureka-client/          ← cliente Eureka de ejemplo (puerto 8081)
├── config-client/          ← cliente Config Server con perfiles (puerto 8082)
├── api-gateway/            ← punto de entrada único con enrutamiento dinámico (puerto 8090)
├── servicio-productos/     ← CRUD reactivo + consumidor Kafka PedidoCreado (puerto 8083)
├── servicio-pedidos/       ← CRUD reactivo + Circuit Breaker + productor Kafka (puerto 8084)
└── admin-server/           ← Spring Boot Admin 4.0.4, monitorización del ecosistema (puerto 9090)
```

---

## Convenciones

- **groupId:** `com.cursosdedesarrollo`
- **Configuración:** exclusivamente en `application.yml`, nunca `.properties`
- **Lombok:** disponible en todos los módulos sin declaración adicional
- **APIs reactivas:** Spring WebFlux + Project Reactor (`Mono`/`Flux`) siempre que aplique
- **Dependencias:** cada módulo declara solo las suyas; versiones gestionadas por el BOM raíz
