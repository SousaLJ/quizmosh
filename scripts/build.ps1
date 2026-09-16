$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
npm ci --prefix quizmosh-web --no-audit --no-fund
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
npm run build --prefix quizmosh-web
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
New-Item -ItemType Directory -Force quizmosh-server/src/main/resources/static | Out-Null
Copy-Item -Recurse -Force quizmosh-web/dist/* quizmosh-server/src/main/resources/static/
& .\mvnw.cmd -B -ntp clean verify
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
New-Item -ItemType Directory -Force releases | Out-Null
Copy-Item -Force quizmosh-server/target/quizmosh-server-0.1.0-SNAPSHOT.jar releases/quizmosh.jar
Write-Host 'Build concluido: releases/quizmosh.jar'
