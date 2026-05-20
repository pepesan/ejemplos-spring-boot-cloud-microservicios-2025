#!/bin/bash
GROUP=${1:?Uso: $0 <nombre-grupo>}
docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group "$GROUP"
