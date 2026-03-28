@echo off
setlocal EnableExtensions

set ROOT_DIR=%~dp0
cd /d "%ROOT_DIR%"

set ALL_JAR=
for %%f in (target\*-all.jar) do set ALL_JAR=%%~nxf

if "%ALL_JAR%"=="" echo [ERROR] Fat jar not found. Please run build.cmd first.& exit /b 1

java -jar "target\%ALL_JAR%"
exit /b %errorlevel%
