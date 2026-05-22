# consul-client

Microservicio de ejemplo que ilustra dos capacidades de **Consul** como plataforma de microservicios:

1. **Service Discovery** — registro automático y consulta del catálogo de servicios.
2. **Configuración centralizada** — lectura de propiedades desde el KV store de Consul.

## Stack

| Tecnología                       | Uso                                              |
|----------------------------------|--------------------------------------------------|
| Spring Boot 4.0.5 + WebFlux      | Runtime reactivo                                 |
| Spring Cloud Consul Discovery    | Registro automático ante Consul                  |
| Spring Cloud Consul Config       | Propiedades leídas del KV store de Consul        |
| Spring Data R2DBC + H2 / MySQL   | CRUD reactivo de Tareas (H2 en dev, MySQL en prod) |
| Micrometer Tracing + Zipkin      | Trazas distribuidas                              |
| Lombok                           | Reducción de boilerplate                         |

## Puerto

`8085` — http://localhost:8085

## Prerrequisitos

```bash
docker compose -f docker/compose.yaml up -d consul
```

UI de Consul: http://localhost:8500/ui

## Arranque

```bash
# Sin perfil — usa config de config/consul-client/data (H2 in-memory)
./gradlew :consul-client:bootRun

# Perfil desarrollo — fusiona /data + config/consul-client,desarrollo/data
./gradlew :consul-client:bootRun --args='--spring.profiles.active=desarrollo'

# Perfil produccion — fusiona /data + config/consul-client,produccion/data (MySQL)
./gradlew :consul-client:bootRun --args='--spring.profiles.active=produccion'
```

Al arrancar el servicio:

1. Carga configuración desde `config/consul-client/data` en el KV de Consul.
2. Se registra en Consul con el nombre `consul-client`.
3. Declara un health-check HTTP: Consul hace poll a `/actuator/health` cada 10 s.

## Endpoints

### Diagnóstico y descubrimiento

| Endpoint                     | Método | Descripción                                                       |
|------------------------------|--------|-------------------------------------------------------------------|
| `/`                          | `GET`  | Confirmación de que el servicio está activo                       |
| `/hola`                      | `GET`  | Saludo simple                                                     |
| `/config`                    | `GET`  | Propiedades generales leídas desde Consul KV                      |
| `/db-config`                 | `GET`  | Config de BD activa para el perfil en curso (password enmascarada)|
| `/servicios`                 | `GET`  | Nombres de todos los servicios registrados en Consul              |
| `/instancias`                | `GET`  | Instancias del propio servicio `consul-client`                    |
| `/instancias/{serviceId}`    | `GET`  | Instancias de cualquier servicio registrado en Consul             |

```bash
curl http://localhost:8085/hola
curl http://localhost:8085/config
curl http://localhost:8085/db-config
curl http://localhost:8085/servicios | jq
curl http://localhost:8085/instancias | jq
```

### CRUD de Tareas (R2DBC)

| Endpoint           | Método   | Descripción                                              |
|--------------------|----------|----------------------------------------------------------|
| `/tareas`          | `GET`    | Lista todas las tareas. `?completada=true/false` filtra  |
| `/tareas/{id}`     | `GET`    | Obtiene una tarea por ID (404 si no existe)              |
| `/tareas`          | `POST`   | Crea una nueva tarea (201 Created)                       |
| `/tareas/{id}`     | `PUT`    | Actualiza una tarea existente (404 si no existe)         |
| `/tareas/{id}`     | `DELETE` | Elimina una tarea (204 No Content, 404 si no existe)     |

```bash
# Crear
curl -s -X POST http://localhost:8085/tareas \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Comprar leche","descripcion":"Entera, 2 litros","completada":false}' | jq

# Listar todas
curl -s http://localhost:8085/tareas | jq

# Filtrar por completada
curl -s 'http://localhost:8085/tareas?completada=false' | jq

# Obtener por ID
curl -s http://localhost:8085/tareas/1 | jq

# Actualizar
curl -s -X PUT http://localhost:8085/tareas/1 \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Comprar leche","descripcion":"Entera, 2 litros","completada":true}' | jq

# Eliminar
curl -s -X DELETE http://localhost:8085/tareas/1
```

---

## Configuración centralizada con Consul KV

### Cómo funciona

Spring Cloud Consul Config lee el KV store de Consul al arrancar usando `spring.config.import=optional:consul:`.

La ruta donde debe estar el YAML de configuración es:

```
config/consul-client/data
```

| Segmento        | Origen                                    |
|-----------------|-------------------------------------------|
| `config`        | `spring.cloud.consul.config.prefixes[0]`  |
| `consul-client` | `spring.application.name`                |
| `data`          | `spring.cloud.consul.config.data-key`    |

### Cargar configuración en Consul — vía UI

1. Abre http://localhost:8500/ui → **Key/Value**
2. Crea la clave `config/consul-client/data`
3. Pega este valor como contenido:

```yaml
consulclient:
  mensaje: "Hola desde Consul KV!"
  limite: 42
  entorno: "consul"
```

4. Haz clic en **Save**

### Cargar configuración en Consul — vía CLI

```bash
consul kv put config/consul-client/data \
'consulclient:
  mensaje: "Hola desde Consul KV!"
  limite: 42
  entorno: "consul"'
```

O con Docker:

```bash
docker exec consul consul kv put config/consul-client/data \
'consulclient:
  mensaje: "Hola desde Consul KV!"
  limite: 42
  entorno: "consul"'
```

### Verificar que se leyó la configuración

```bash
curl http://localhost:8085/config
```

Respuesta esperada:

```json
{
  "mensaje": "Hola desde Consul KV!",
  "limite": 42,
  "entorno": "consul"
}
```

Si Consul KV no tiene el valor, se devuelven los defaults definidos en `ConsulConfigProperties`:

```json
{
  "mensaje": "configuración local (Consul KV no disponible)",
  "limite": 100,
  "entorno": "local"
}
```

### Recarga automática sin reiniciar

El watcher de Spring Cloud Consul (`spring.cloud.consul.config.watch.enabled=true`) detecta cambios en el KV y refresca los beans anotados con `@RefreshScope` sin reiniciar el servicio.

| Bean | `@RefreshScope` | Recarga en caliente |
|---|---|---|
| `ConsulConfigProperties` | ✅ | Sí — `/config` devuelve el nuevo valor en la siguiente petición |
| `DatabaseProperties` | ❌ | No — cambiar URL/credenciales de BD requiere reiniciar el servicio |

---

## Configuración de BD por perfil desde Consul KV

### Cómo funciona la resolución por perfil

Spring Cloud Consul Config fusiona las claves KV en este orden (de menor a mayor prioridad):

```
config/application/data              ← compartida por todos los servicios
config/consul-client/data            ← por defecto (sin perfil)
config/consul-client,{perfil}/data   ← sobreescritura para el perfil activo
```

Cada capa sobreescribe solo las propiedades que declara. Es el mismo comportamiento que `application.yml` + `application-{perfil}.yml` en Spring Boot.

### Rutas KV que hay que crear

| Perfil     | Clave KV                                | BD                   |
|------------|-----------------------------------------|----------------------|
| (ninguno)  | `config/consul-client/data`             | H2 in-memory         |
| desarrollo | `config/consul-client,desarrollo/data`  | H2 in-memory (dev)   |
| produccion | `config/consul-client,produccion/data`  | MySQL                |

> Las URLs deben usar el protocolo **R2DBC** (`r2dbc:h2:…`, `r2dbc:mysql:…`), no JDBC.

### Cargar las claves KV (CLI con Docker)

**Sin perfil — valores por defecto (H2 in-memory):**

```bash
docker exec consul consul kv put config/consul-client/data \
'consulclient:
  mensaje: "consul-client arrancado"
  limite: 50
  entorno: "sin perfil"
  datasource:
    url: "r2dbc:h2:mem:///defaultdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
    username: "sa"
    password: ""
    poolSize: 5'
```

**Perfil `desarrollo` (H2 in-memory):**

```bash
docker exec consul consul kv put config/consul-client,desarrollo/data \
'consulclient:
  entorno: "desarrollo"
  datasource:
    url: "r2dbc:h2:mem:///devdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
    username: "sa"
    password: ""
    poolSize: 5'
```

**Perfil `produccion` (MySQL):**

```bash
docker exec consul consul kv put config/consul-client,produccion/data \
'consulclient:
  entorno: "produccion"
  datasource:
    url: "r2dbc:mysql://prod-db:3306/appdb"
    username: "app_user"
    password: "s3cr3t_pr0d"
    poolSize: 20'
```

> Liquibase aplica las migraciones en todas las BDs (H2 y MySQL) al arrancar.
> No hay `schema.sql` — el esquema se gestiona exclusivamente mediante changelogs Liquibase.

### Arrancar con cada perfil

```bash
# Sin perfil (carga config/consul-client/data)
./gradlew :consul-client:bootRun

# Perfil desarrollo (fusiona /data + ,desarrollo/data)
./gradlew :consul-client:bootRun --args='--spring.profiles.active=desarrollo'

# Perfil produccion (fusiona /data + ,produccion/data)
./gradlew :consul-client:bootRun --args='--spring.profiles.active=produccion'
```

### Verificar la config de BD activa

```bash
curl http://localhost:8085/db-config
```

Respuesta con perfil `desarrollo`:

```json
{"url":"r2dbc:h2:mem:///devdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","username":"sa","password":"(no configurado)","poolSize":5}
```

Respuesta con perfil `produccion`:

```json
{"url":"r2dbc:mysql://prod-db:3306/appdb","username":"app_user","password":"***","poolSize":20}
```

Respuesta sin perfil (defaults locales si Consul KV no tiene valor):

```json
{"url":"r2dbc:h2:mem:///localdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","username":"sa","password":"(no configurado)","poolSize":5}
```

---

## Configuración relevante en application.yml

```yaml
spring:
  config:
    import: "optional:consul:"   # activa la carga desde Consul KV
  cloud:
    consul:
      host: localhost
      port: 8500
      discovery:
        prefer-ip-address: true
        health-check-interval: 10s
        health-check-path: /actuator/health
      config:
        format: YAML             # el valor del KV es un bloque YAML
        data-key: data           # nombre de la clave dentro de la carpeta
        prefixes:                # reemplaza 'prefix' (deprecado)
          - config               # carpeta raíz en el KV
        watch:
          enabled: true          # detecta cambios y refresca @RefreshScope beans

management:
  tracing:
    sampling:
      probability: 1.0
    export:
      zipkin:
        endpoint: http://localhost:9411/api/v2/spans
```

---

## Migraciones de base de datos (Liquibase)

El esquema se gestiona con **Liquibase** (no con `schema.sql`). El changelog se encuentra en `src/main/resources/db/changelog/db.changelog-master.yaml`.

Liquibase requiere JDBC, pero este módulo es WebFlux puro (R2DBC). La clase `LiquibaseConfig` deriva la URL JDBC desde la URL R2DBC de `DatabaseProperties` y crea un `SpringLiquibase` explícito. Al cambiar la URL de BD en Consul KV, Liquibase apunta automáticamente a la nueva BD sin ningún cambio de código.

Las migraciones se ejecutan automáticamente al arrancar. No hay que crear el esquema manualmente.

---

## Prueba manual con curl

Con el servicio arrancado (`./gradlew :consul-client:bootRun`) y Consul disponible:

### Diagnóstico

```bash
# Estado del servicio
curl -s http://localhost:8085/hola

# Configuración activa (desde Consul KV o defaults)
curl -s http://localhost:8085/config | jq

# Config de base de datos del perfil activo
curl -s http://localhost:8085/db-config | jq

# Servicios registrados en Consul
curl -s http://localhost:8085/servicios | jq

# Instancias del propio consul-client
curl -s http://localhost:8085/instancias | jq
```

### CRUD de tareas

```bash
# Crear tarea (devuelve 201 con el objeto creado e ID generado)
curl -s -X POST http://localhost:8085/tareas \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Hacer la compra","descripcion":"Leche, pan y huevos","completada":false}' | jq

# Listar todas las tareas
curl -s http://localhost:8085/tareas | jq

# Filtrar por estado
curl -s 'http://localhost:8085/tareas?completada=false' | jq
curl -s 'http://localhost:8085/tareas?completada=true' | jq

# Obtener tarea por ID
curl -s http://localhost:8085/tareas/1 | jq

# Actualizar tarea (devuelve 200; 404 si no existe)
curl -s -X PUT http://localhost:8085/tareas/1 \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Hacer la compra","descripcion":"Lista actualizada","completada":true}' | jq

# Eliminar tarea (devuelve 204 sin cuerpo; 404 si no existe)
curl -s -o /dev/null -w "%{http_code}" -X DELETE http://localhost:8085/tareas/1
```

---

## Tests

```bash
./gradlew :consul-client:test
```

Los tests de integración arrancan el contexto completo con `RANDOM_PORT` y usan H2 in-memory.
Consul (discovery + config) se deshabilita para que los tests sean autónomos.

> **Nota H2 2.x + Liquibase:** la URL de test incluye `CASE_INSENSITIVE_IDENTIFIERS=TRUE` (Spring Data R2DBC genera
> columnas entre comillas en mayúsculas y H2 2.x es case-sensitive con comillas). Liquibase usa JDBC internamente
> con `user=sa`; la URL R2DBC debe incluir `spring.r2dbc.username=sa` (o vía `consulclient.datasource.username`)
> para que ambas conexiones usen el mismo propietario de BD.
