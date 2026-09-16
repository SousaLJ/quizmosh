#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf 'QuizMosh: abra http://localhost:8080 depois da mensagem Started QuizMoshServer.\n'
exec java -jar releases/quizmosh.jar
