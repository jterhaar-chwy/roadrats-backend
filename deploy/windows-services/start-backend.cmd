@echo off
setlocal

set "APP_HOME=%~dp0"
if "%APP_HOME:~-1%"=="\" set "APP_HOME=%APP_HOME:~0,-1%"

set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=java.exe"

set "JAR_PATH=%APP_HOME%\demo-0.0.1-SNAPSHOT.jar"
set "NATIVE_LIBS=%APP_HOME%\native-libs"

if not exist "%JAR_PATH%" (
  echo Backend jar not found: %JAR_PATH%
  exit /b 1
)

"%JAVA_EXE%" -Djava.library.path="%NATIVE_LIBS%" -jar "%JAR_PATH%"
exit /b %ERRORLEVEL%
