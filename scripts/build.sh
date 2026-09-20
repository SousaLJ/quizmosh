#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
npm ci --prefix quizmosh-web --no-audit --no-fund
npm run build --prefix quizmosh-web
mkdir -p quizmosh-server/src/main/resources/static
cp -R quizmosh-web/dist/. quizmosh-server/src/main/resources/static/
./mvnw -B -ntp clean verify
mkdir -p releases
cp quizmosh-server/target/quizmosh-server-0.1.0-SNAPSHOT.jar releases/ludrivo.jar
printf '\nBuild concluido: releases/ludrivo.jar\n'
