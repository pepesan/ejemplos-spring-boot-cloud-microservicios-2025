# Ejemplos Spring Boot Cloud Microservicios

Colección de ejemplos prácticos de microservicios con Spring Boot 4 y Spring Cloud. Cada módulo ilustra un componente o patrón concreto del ecosistema Spring Cloud.

## Stack tecnológico

| Tecnología              | Versión                  |
|-------------------------|--------------------------|
| Java                    | 25                       |
| Spring Boot             | 4.0.5                    |
| Spring Cloud            | 2025.1.1 (Oakwood)       |
| Spring Boot Admin       | 4.0.4                    |
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
