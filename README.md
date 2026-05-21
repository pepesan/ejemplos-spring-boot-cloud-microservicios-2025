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
├── eureka-server/          ← servidor de registro y descubrimiento
└── eureka-client/          ← microservicio cliente que se registra en Eureka
```

## Módulos

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
# 1. Arrancar primero el registro
./gradlew :eureka-server:bootRun

# 2. Arrancar el cliente (en otra terminal)
./gradlew :eureka-client:bootRun

# 3. Verificar el registro en el dashboard
# http://localhost:8761

# 4. Probar los endpoints del cliente
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

# Arrancar un servicio
./gradlew :eureka-server:bootRun
./gradlew :eureka-client:bootRun

# Ejecutar tests de un módulo
./gradlew :eureka-server:test
./gradlew :eureka-client:test

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

Sustituir el puerto `8761` por el del módulo correspondiente (`8081` para `eureka-client`).

## Convenciones de los módulos

- **groupId:** `com.cursosdedesarrollo`
- **Configuración:** exclusivamente en `application.yml`
- **Lombok:** disponible en todos los módulos sin declaración adicional
- **APIs reactivas:** se usa Spring WebFlux + Project Reactor (`Mono`/`Flux`) siempre que aplique

Cada módulo solo declara en su `build.gradle.kts` las dependencias específicas que añade; las versiones y dependencias comunes (Lombok, test) las gestiona el BOM del raíz.
