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
└── eureka-server/          ← servidor de registro y descubrimiento
```

## Módulos

### eureka-server

Servidor de registro y descubrimiento basado en Netflix Eureka. Todos los microservicios del ecosistema se registran aquí y lo consultan para localizar otros servicios.

- **Puerto:** `8761`
- **Dashboard:** `http://localhost:8761`
- **Dependencia clave:** `spring-cloud-starter-netflix-eureka-server`

Para que un microservicio se registre en este servidor debe incluir `spring-cloud-starter-netflix-eureka-client` y apuntar su `eureka.client.service-url.defaultZone` a `http://localhost:8761/eureka/`.

## Comandos

```bash
# Construir todos los módulos
./gradlew build

# Construir un módulo concreto
./gradlew :eureka-server:build

# Arrancar el servidor Eureka
./gradlew :eureka-server:bootRun

# Ejecutar tests de un módulo
./gradlew :eureka-server:test

# Ejecutar una clase de test concreta
./gradlew :eureka-server:test --tests "com.cursosdedesarrollo.eurekaserver.EurekaServerApplicationTest"

# Construir sin tests
./gradlew build -x test
```

## Convenciones de los módulos

- **groupId:** `com.cursosdedesarrollo`
- **Configuración:** exclusivamente en `application.yml`
- **Lombok:** disponible en todos los módulos sin declaración adicional
- **APIs reactivas:** se usa Spring WebFlux + Project Reactor (`Mono`/`Flux`) siempre que aplique

Cada módulo solo declara en su `build.gradle.kts` las dependencias específicas que añade; las versiones y dependencias comunes (Lombok, test) las gestiona el BOM del raíz.
