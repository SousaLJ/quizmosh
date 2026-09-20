#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v docker >/dev/null; then printf 'Instale e abra o Docker Desktop ou Docker Engine.\n'; exit 1; fi
docker compose version >/dev/null
if [ ! -e .env ]; then
  umask 077
  password=$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')
  printf 'POSTGRES_PASSWORD=%s\nHTTP_PORT=8080\nHTTPS_PORT=8443\nSITE_ADDRESS=:80\nMAX_ROOMS=250\n' "$password" > .env
fi
docker compose up -d --build --wait
printf '\nLudrivo pronto. Abra http://localhost:8080 (ou a porta escolhida no .env).\n'
