@echo off
rem ============================================================
rem  Flight Query System - one-click starter (Java backend)
rem  Usage: double-click this file, then open http://localhost:8080/
rem ============================================================
set "JAVA_HOME=D:\coder\jdk1.8.0_201"
cd /d "%~dp0"

if not exist "target\flight-server-1.0.0.jar" (
  echo [ERROR] jar not found. Please run: mvn package -DskipTests
  pause
  exit /b 1
)

echo Starting flight-server ...
echo Open http://localhost:8080/ in your browser
echo.

"%JAVA_HOME%\bin\java" -Dfile.encoding=UTF-8 -jar "target\flight-server-1.0.0.jar" --server.port=8080
pause
