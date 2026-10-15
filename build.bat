@echo off
setlocal

REM === Ajusta esta ruta a tu JDK 21 (jpackage no existe en Java 11) ===
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set APP_NAME=ConsultaClientes
set APP_VERSION=1.0.0
set SALIDA=target\installer

echo [1/3] Compilando con Maven...
call mvn -q clean package
if errorlevel 1 goto error

echo [2/3] Generando ejecutable con jpackage...
jpackage ^
  --type app-image ^
  --name %APP_NAME% ^
  --app-version %APP_VERSION% ^
  --vendor "Mi Empresa" ^
  --input target\dist ^
  --main-jar consulta-clientes.jar ^
  --main-class com.empresa.consulta.App ^
  --dest %SALIDA%
if errorlevel 1 goto error

echo [3/3] Copiando config.properties junto al .exe...
copy /Y config\config.properties %SALIDA%\%APP_NAME%\ >nul
if errorlevel 1 goto error
if exist config\logo.png (
  copy /Y config\logo.png %SALIDA%\%APP_NAME%\ >nul
) else (
  echo     Aviso: no hay config\logo.png, la app se mostrara sin logo.
)

echo.
echo Listo: %SALIDA%\%APP_NAME%\%APP_NAME%.exe
echo Comprime la carpeta %SALIDA%\%APP_NAME% en un .zip para compartirla.
exit /b 0

:error
echo.
echo *** Fallo la compilacion ***
exit /b 1
