#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

echo "== containers =="
docker compose ps

echo "== mysql =="
docker compose exec -T mysql mysqladmin ping -h 127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent

echo "== redis-core =="
docker compose exec -T redis-core redis-cli ping

echo "== redis-cache =="
docker compose exec -T redis-cache redis-cli ping

echo "== rabbitmq =="
docker compose exec -T rabbitmq rabbitmq-diagnostics -q ping

echo "== elasticsearch =="
curl -fsS "http://${BIND_IP:-192.168.157.129}:9200/_cluster/health?pretty"

echo "== redis policies =="
docker compose exec -T redis-core redis-cli config get maxmemory-policy
docker compose exec -T redis-cache redis-cli config get maxmemory-policy

echo "OK"
