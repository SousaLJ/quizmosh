#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf 'Ludrivo: abra http://localhost:8080 depois da mensagem Started QuizMoshServer.\n'
exec java -jar releases/ludrivo.jar
