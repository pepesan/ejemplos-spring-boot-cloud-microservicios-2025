# Servicios externos — Docker Compose

Infraestructura necesaria para ejecutar el ecosistema de microservicios en local.

## Servicios

| Servicio   | URL                          | Descripción                              |
|------------|------------------------------|------------------------------------------|
| Kafka      | `localhost:9092`             | Broker KRaft (sin ZooKeeper) — apache/kafka 4.2.0 |
| Kafka UI   | http://localhost:8081        | Interfaz web para topics y mensajes      |
| Zipkin     | http://localhost:9411        | Trazabilidad distribuida                 |

## Arrancar y parar

```bash
./01_launch.sh        # arranca todos los servicios en background
./02_ps.sh            # estado de los contenedores
./03_logs.sh          # logs en tiempo real
./20_destroy.sh       # para y elimina contenedores y volúmenes
```

## Gestión de topics Kafka

```bash
./04_create_topic.sh [nombre-topic]     # crea un topic (defecto: pedidos-creados)
./05_list_topics.sh                     # lista todos los topics
./08_describe_topic.sh [nombre-topic]   # detalle de particiones y offsets
```

## Producir y consumir mensajes desde consola

```bash
./06_produce_message.sh [nombre-topic]  # productor interactivo (Enter para enviar, Ctrl+C para salir)
./07_consume_message.sh [nombre-topic]  # consumidor desde el principio
```

## Consumer groups

```bash
./09_list_consumer_groups.sh            # lista los grupos
./10_describe_consumer_group.sh <grupo> # detalle de lag por partición
```

## Topics del proyecto

| Topic            | Productor           | Consumidor            |
|------------------|---------------------|-----------------------|
| `pedidos-creados`| `servicio-pedidos`  | `servicio-productos`  |
