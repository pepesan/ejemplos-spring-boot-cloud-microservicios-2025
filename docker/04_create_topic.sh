#!/bin/bash
TOPIC=${1:-pedidos-creados}
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic "$TOPIC" \
  --partitions 3 \
  --replication-factor 1
