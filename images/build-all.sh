#!/usr/bin/env bash
# Ejecutar desde la raíz del proyecto: ./images/build-all.sh
set -euo pipefail

DOCKER_USER="pepesan"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$ROOT_DIR"

SERVICES=(
  "eureka-server"
  "config-server"
  "eureka-client"
  "config-client"
  "api-gateway"
  "servicio-productos"
  "servicio-pedidos"
  "admin-server"
)

echo "==> Compilando JARs (sin tests)..."
./gradlew \
  :eureka-server:bootJar \
  :config-server:bootJar \
  :eureka-client:bootJar \
  :config-client:bootJar \
  :api-gateway:bootJar \
  :servicio-productos:bootJar \
  :servicio-pedidos:bootJar \
  :admin-server:bootJar \
  -x test

echo ""
echo "==> Construyendo imágenes Docker..."

for SERVICE in "${SERVICES[@]}"; do
  echo ""
  echo "  [${SERVICE}] --> ${DOCKER_USER}/${SERVICE}:latest"
  docker build \
    -f "images/Dockerfile.${SERVICE}" \
    -t "${DOCKER_USER}/${SERVICE}:latest" \
    .
done

echo ""
echo "Imágenes listas:"
for SERVICE in "${SERVICES[@]}"; do
  echo "  ${DOCKER_USER}/${SERVICE}:latest"
done
