#!/bin/bash
TOPIC=${1:-pedidos-creados}
docker exec -it kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic "$TOPIC"
# Escribe mensajes y pulsa Enter. Ctrl+C para salir.
