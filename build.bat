@echo off
setlocal

REM === Configuracion de rutas a Java 21 y Maven ===
set JAVA_HOME=C:\Users\nem.miztli\java\jdk-21
set MAVEN_HOME=C:\Users\nem.miztli\java\apache-maven-3.9.16
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

set APP_NAME=ValidadorEdoCta
set APP_VERSION=1.0.0
set SALIDA=target\installer

REM === Java incluido: solo los modulos que usa la aplicacion (jdeps + los que se cargan en ejecucion:
REM     cifrado TLS, certificados de Windows, idioma es/en). Si al abrir falla con NoClassDefFoundError
REM     o ClassNotFoundException de una clase java.* / javax.*, agregar aqui el modulo que falte.
set MODULOS=java.base,java.desktop,java.logging,java.management,java.naming,java.prefs,java.security.jgss,java.sql,jdk.charsets,jdk.crypto.ec,jdk.crypto.mscapi,jdk.localedata,jdk.security.auth,jdk.unsupported
set JLINK_OPCIONES=--strip-debug --no-man-pages --no-header-files --strip-native-commands --include-locales=en,es --compress=zip-6

echo [1/3] Compilando con Maven...
call mvn -q clean package
if errorlevel 1 goto error

echo [2/3] Generando ejecutable con jpackage...
call jpackage --type app-image --name %APP_NAME% --app-version %APP_VERSION% --input target\dist --main-jar validador-edocta.jar --main-class com.coppel.validador.App --icon packaging\icono.ico --add-modules %MODULOS% --jlink-options "%JLINK_OPCIONES%" --dest %SALIDA%
if errorlevel 1 goto error

echo [3/3] Copiando config.properties y bonificaciones.csv junto al .exe...
copy /Y config\config.properties %SALIDA%\%APP_NAME%\ >nul
if errorlevel 1 goto error
copy /Y src\main\resources\bonificaciones.csv %SALIDA%\%APP_NAME%\ >nul
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
