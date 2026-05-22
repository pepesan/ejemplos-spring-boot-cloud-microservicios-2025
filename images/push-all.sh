#!/usr/bin/env bash
# Ejecutar desde la raíz del proyecto: ./images/push-all.sh
# Requiere haber hecho antes: docker login
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

echo "==> Subiendo imágenes a Docker Hub (${DOCKER_USER})..."
echo "    Si no has hecho login: docker login"
echo ""

for SERVICE in "${SERVICES[@]}"; do
  echo "  --> ${DOCKER_USER}/${SERVICE}:latest"
  docker push "${DOCKER_USER}/${SERVICE}:latest"
done

echo ""
echo "Imágenes publicadas:"
for SERVICE in "${SERVICES[@]}"; do
  echo "  https://hub.docker.com/r/${DOCKER_USER}/${SERVICE}"
done
