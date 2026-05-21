# servicio-productos

Microservicio reactivo de gestión de catálogo de productos. Expone un CRUD completo
mediante Spring WebFlux + R2DBC (H2 en memoria) y consume eventos Kafka de tipo
`PedidoCreado` publicados por `servicio-pedidos` para decrementar el stock.

## Stack

| Componente            | Tecnología                                    |
|-----------------------|-----------------------------------------------|
| Framework web         | Spring WebFlux (Netty)                        |
| Persistencia          | Spring Data R2DBC + H2 en memoria            |
| Mensajería            | Spring Cloud Stream · Kafka binder            |
| Registro de servicio  | Spring Cloud Netflix Eureka Client            |
| Configuración externa | Spring Cloud Config Client                    |
| Monitorización        | Spring Boot Actuator                          |
| Boilerplate           | Lombok                                        |

## Puerto

| Entorno    | Puerto |
|------------|--------|
| Desarrollo | 8083   |

## Endpoints REST

| Método   | Path               | Descripción                              | Respuesta exitosa |
|----------|--------------------|------------------------------------------|-------------------|
| `GET`    | `/productos`       | Lista todos los productos                | 200 + `Flux<Producto>` |
| `GET`    | `/productos/{id}`  | Obtiene un producto por id               | 200 + `Mono<Producto>` / 404 |
| `POST`   | `/productos`       | Crea un producto nuevo                   | 201 + `Mono<Producto>` |
| `PUT`    | `/productos/{id}`  | Actualiza un producto existente          | 200 + `Mono<Producto>` / 404 |
| `DELETE` | `/productos/{id}`  | Elimina un producto por id               | 204 / 404        |

### Ejemplo de uso directo

```bash
# Listar productos
curl http://localhost:8083/productos

# Obtener producto con id 1
curl http://localhost:8083/productos/1

# Crear producto
curl -X POST http://localhost:8083/productos \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Hub USB-C","descripcion":"7 puertos USB 3.2","precio":39.99,"stock":75}'

# Actualizar producto
curl -X PUT http://localhost:8083/productos/1 \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Teclado mecánico PRO","descripcion":"RGB","precio":109.99,"stock":40}'

# Eliminar producto
curl -X DELETE http://localhost:8083/productos/1
```

### Ejemplo de uso a través del API Gateway (puerto 8090)

El gateway elimina el prefijo `/servicio-productos` antes de reenviar la petición,
por lo que el comportamiento es idéntico al acceso directo.

```bash
# Listar todos los productos
curl http://localhost:8090/servicio-productos/productos

# Obtener producto por id (404 si no existe)
curl http://localhost:8090/servicio-productos/productos/2

# Crear producto (devuelve 201)
curl -X POST http://localhost:8090/servicio-productos/productos \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Hub USB-C","descripcion":"7 puertos USB 3.2","precio":39.99,"stock":75}'

# Actualizar producto (404 si no existe)
curl -X PUT http://localhost:8090/servicio-productos/productos/1 \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Teclado mecánico PRO","descripcion":"RGB","precio":109.99,"stock":40}'

# Eliminar producto (204 si existe, 404 si no)
curl -X DELETE http://localhost:8090/servicio-productos/productos/1
```

## Mensajería Kafka

El servicio escucha el topic `pedidos-creados` esperando eventos con la estructura:

```json
{
  "pedidoId": 1,
  "productoId": 2,
  "cantidad": 3
}
```

Al recibir el evento, decrementa el stock del producto indicado. El stock nunca baja de 0.

### Infraestructura necesaria

Para que la mensajería funcione es necesario tener Kafka arrancado. El proyecto
incluye un `docker/compose.yaml` con Kafka y Kafka UI:

```bash
# Desde la raíz del proyecto
docker compose -f docker/compose.yaml up -d
```

Kafka UI estará disponible en `http://localhost:9001`.

## Configuración externa

El servicio carga su configuración desde el Config Server en:

```
config-repo/servicio-productos/servicio-productos.yml
```

Las propiedades configuradas externamente son:

| Propiedad                                       | Valor por defecto          | Descripción                        |
|-------------------------------------------------|----------------------------|------------------------------------|
| `server.port`                                   | 8083                       | Puerto HTTP                        |
| `spring.r2dbc.url`                              | `r2dbc:h2:mem:///productosdb` | Base de datos reactiva en memoria |
| `spring.cloud.stream.bindings.procesarPedido-in-0.destination` | `pedidos-creados` | Topic Kafka |
| `spring.cloud.stream.kafka.binder.brokers`      | `localhost:9092`           | Broker Kafka                       |

## Base de datos

H2 en memoria gestionada mediante R2DBC. Al arrancar, Spring Boot ejecuta automáticamente
`src/main/resources/schema.sql` que crea la tabla `producto` e inserta 5 productos
de ejemplo.

La base de datos **no persiste** entre reinicios del servicio, lo que la hace ideal
para ejemplos y tests.

## Tests

Los tests usan el `TestChannelBinder` de Spring Cloud Stream para sustituir el
broker Kafka real, permitiendo ejecutarlos sin infraestructura externa.

Eureka y el Config Server también se deshabilitan durante los tests mediante
`@TestPropertySource`.

```bash
# Ejecutar tests del módulo
./gradlew :servicio-productos:test

# Ejecutar una clase concreta
./gradlew :servicio-productos:test --tests "com.cursosdedesarrollo.servicioproductos.ServicioProductosApplicationTest"
```

### Cobertura de los tests

| Test                                       | Qué verifica                                        |
|--------------------------------------------|-----------------------------------------------------|
| `contextLoads`                             | El contexto Spring arranca sin errores              |
| `findAll_debeRetornarProductosIniciales`   | GET /productos devuelve los 5 productos del schema  |
| `findById_conIdExistente_debeRetornar200`  | GET /productos/1 devuelve el producto               |
| `findById_conIdInexistente_debeRetornar404`| GET /productos/9999 devuelve 404                    |
| `create_debeGuardarProductoYRetornar201`   | POST /productos crea y devuelve 201                 |
| `update_conIdExistente_debeActualizarProducto` | PUT /productos/1 actualiza el producto          |
| `deleteById_conIdExistente_debeRetornar204`| DELETE /productos/5 devuelve 204                    |
| `deleteById_conIdInexistente_debeRetornar404` | DELETE /productos/9999 devuelve 404              |
| `procesarPedido_debeDecrementarStock`      | Evento Kafka decrementa el stock correctamente      |
| `decrementarStock_noBajaDeZero`            | El stock nunca es negativo aunque la cantidad supere el stock |

## Arranque

Requiere que estén arrancados previamente:

1. `eureka-server` (puerto 8761)
2. `config-server` (puerto 8888)
3. Kafka (puerto 9092) — vía `docker compose -f docker/compose.yaml up -d`

```bash
./gradlew :servicio-productos:bootRun
```
