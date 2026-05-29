@echo off
setlocal

set "APP_HOME=%~dp0"
if "%APP_HOME:~-1%"=="\" set "APP_HOME=%APP_HOME:~0,-1%"

set "NODE_EXE=%ProgramFiles%\nodejs\node.exe"
if not exist "%NODE_EXE%" set "NODE_EXE=node.exe"

set "NEXT_TELEMETRY_DISABLED=1"

set "NEXT_BIN=%APP_HOME%\node_modules\next\dist\bin\next"

if not exist "%NEXT_BIN%" (
  echo Next.js runtime not found: %NEXT_BIN%
  exit /b 1
)

"%NODE_EXE%" "%NEXT_BIN%" start -p 3000
exit /b %ERRORLEVEL%
