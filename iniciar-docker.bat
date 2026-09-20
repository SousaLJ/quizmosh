@echo off
setlocal
cd /d "%~dp0"
where docker >nul 2>nul
if errorlevel 1 (
  echo Instale e abra o Docker Desktop antes de continuar.
  pause
  exit /b 1
)
if not exist .env powershell -NoProfile -Command "$bytes=New-Object byte[] 24; $rng=[Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($bytes); $rng.Dispose(); $password=([BitConverter]::ToString($bytes)).Replace('-','').ToLower(); [IO.File]::WriteAllText((Join-Path (Get-Location) '.env'), ('POSTGRES_PASSWORD='+$password+[Environment]::NewLine+'HTTP_PORT=8080'+[Environment]::NewLine+'HTTPS_PORT=8443'+[Environment]::NewLine+'SITE_ADDRESS=:80'+[Environment]::NewLine+'MAX_ROOMS=250'+[Environment]::NewLine))"
if errorlevel 1 exit /b 1
docker compose up -d --build --wait
if errorlevel 1 (
  echo Nao foi possivel iniciar. Confira a mensagem acima e se o Docker esta aberto.
  pause
  exit /b 1
)
echo Ludrivo pronto em http://localhost:8080 - use a porta escolhida no .env.
start "" "http://localhost:8080"
pause
