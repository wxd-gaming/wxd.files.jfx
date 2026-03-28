@echo off
setlocal EnableExtensions

set ROOT_DIR=%~dp0
cd /d "%ROOT_DIR%"

set APP_NAME=WxdGamingFolderManager
set APP_VERSION=1.0.0
set VENDOR=WxdGaming
set DIST_DIR=target\dist
set INPUT_DIR=%DIST_DIR%\input
set IMAGE_DIR=%DIST_DIR%\app-image
set ALL_JAR=

call mvn -v >nul 2>nul
if errorlevel 1 echo [ERROR] Maven (mvn) not found in PATH.& exit /b 1

jpackage --version >nul 2>nul
if errorlevel 1 echo [ERROR] jpackage not found in PATH. Please use JDK 14+ and set PATH.& exit /b 1

echo [1/3] Build fat jar...
call mvn -DskipTests clean package
if errorlevel 1 exit /b 1

for %%f in (target\*-all.jar) do set ALL_JAR=%%~nxf
if "%ALL_JAR%"=="" echo [ERROR] Fat jar not found in target\*-all.jar& exit /b 1

echo [2/3] Prepare packaging input...
if exist "%INPUT_DIR%" rmdir /s /q "%INPUT_DIR%"
if exist "%IMAGE_DIR%" rmdir /s /q "%IMAGE_DIR%"
mkdir "%INPUT_DIR%" >nul
mkdir "%IMAGE_DIR%" >nul
copy /y "target\%ALL_JAR%" "%INPUT_DIR%\%ALL_JAR%" >nul

echo [3/3] Build app-image with jpackage...
echo [INFO] This creates a normal runnable .exe (no installer).
echo [INFO] jpackage still bundles runtime by design.

call jpackage --type app-image --name "%APP_NAME%" --app-version "%APP_VERSION%" --vendor "%VENDOR%" --dest "%IMAGE_DIR%" --input "%INPUT_DIR%" --main-jar "%ALL_JAR%" --main-class org.example.Main
if errorlevel 1 exit /b 1

echo.
echo Build completed.
echo Fat jar: %ROOT_DIR%target\%ALL_JAR%
echo App image dir: %ROOT_DIR%%IMAGE_DIR%\%APP_NAME%
echo Exe path: %ROOT_DIR%%IMAGE_DIR%\%APP_NAME%\%APP_NAME%.exe
exit /b 0
