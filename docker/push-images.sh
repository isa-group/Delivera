#!/usr/bin/env bash

set -euo pipefail

readonly VERSION="${1:-latest}"

if [[ -z "${DOCKER_USER:-}" ]]; then
    echo "[ERROR] DOCKER_USER is not defined."
    echo ""
    echo "Example:"
    echo "  export DOCKER_USER=davposmar"
    echo "  ./publish-images.sh v1.0"
    exit 1
fi

echo "[INFO] Publishing images as ${DOCKER_USER}"

echo "[INFO] Building images..."

docker build \
  -t "${DOCKER_USER}/delivera-auth:${VERSION}" \
  -f Dockerfile.auth \
  ..

docker build \
  -t "${DOCKER_USER}/delivera-data:${VERSION}" \
  -f Dockerfile.data \
  ..

docker build \
  -t "${DOCKER_USER}/delivera-service:${VERSION}" \
  -f Dockerfile.delivera \
  ..

docker build \
  -t "${DOCKER_USER}/delivera-frontend:${VERSION}" \
  ../frontend

echo "[INFO] Pushing images..."

docker push "${DOCKER_USER}/delivera-auth:${VERSION}"
docker push "${DOCKER_USER}/delivera-data:${VERSION}"
docker push "${DOCKER_USER}/delivera-service:${VERSION}"
docker push "${DOCKER_USER}/delivera-frontend:${VERSION}"

echo "[INFO] Publish completed."