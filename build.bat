@echo off
setlocal

REM === Configuracion de rutas a Java 21 y Maven ===
set JAVA_HOME=C:\Users\nem.miztli\java\jdk-21
set MAVEN_HOME=C:\Users\nem.miztli\java\apache-maven-3.9.16
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

set APP_NAME=ConsultaClientes
set APP_VERSION=1.0.0
set SALIDA=target\installer

echo [1/3] Compilando con Maven...
call mvn -q clean package
if errorlevel 1 goto error

echo [2/3] Generando ejecutable con jpackage...
call jpackage --type app-image --name ConsultaClientes --app-version 1.0.0 --input target\dist --main-jar consulta-clientes.jar --main-class com.empresa.consulta.App --dest target\installer
if errorlevel 1 goto error

echo [3/3] Copiando config.properties junto al .exe...
copy /Y config\config.properties %SALIDA%\%APP_NAME%\ >nul
if errorlevel 1 goto error
if exist config\logo.png (
  copy /Y config\logo.png %SALIDA%\%APP_NAME%\ >nul
) else (
  echo      Aviso: no hay config\logo.png, la app se mostrara sin logo.
)

echo.
echo Listo: %SALIDA%\%APP_NAME%\%APP_NAME%.exe
echo Comprime la carpeta %SALIDA%\%APP_NAME% en un .zip para compartirla.
echo.
pause
exit /b 0

:error
echo.
echo *** Fallo la compilacion o generacion del ejecutable ***
echo.
pause
exit /b 1
