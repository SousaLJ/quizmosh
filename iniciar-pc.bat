@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Instale o Java 25 para executar esta versao, ou use iniciar-docker.bat.
  pause
  exit /b 1
)
if not exist releases\ludrivo.jar (
  echo O executavel nao foi compilado. Veja o README ou use iniciar-docker.bat.
  pause
  exit /b 1
)
echo Iniciando Ludrivo. Quando aparecer Started QuizMoshServer, abra http://localhost:8080
echo Mantenha esta janela aberta durante as partidas. Ctrl+C encerra o jogo.
java -jar releases\ludrivo.jar
pause
