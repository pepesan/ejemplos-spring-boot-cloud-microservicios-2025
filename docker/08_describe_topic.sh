#!/bin/bash
TOPIC=${1:-pedidos-creados}
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe --topic "$TOPIC"
