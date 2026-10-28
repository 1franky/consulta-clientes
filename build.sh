#!/usr/bin/env bash
# Compila y genera ValidadorEdoCta.app para macOS (Apple Silicon) con Java embebido (equivalente a build.bat
# en Windows): quien la usa no necesita instalar Java ni nada más.
# Uso:
#   ./build.sh              compila y genera target/installer/ValidadorEdoCta/ (con su .zip para compartir)
#   ./build.sh --solo-jar   solo compila el jar (target/dist/validador-edocta.jar)
set -euo pipefail

cd "$(dirname "$0")"

# === Java 21 (el mismo que usa build.bat); se puede forzar otro con JAVA_HOME=... ./build.sh ===
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21 -a arm64)}"
export PATH="$JAVA_HOME/bin:$PATH"

APP_NAME=ValidadorEdoCta
APP_VERSION=1.0.0
DIST=target/dist
SALIDA=target/installer/$APP_NAME

# === Java incluido: los mismos módulos que build.bat, sin jdk.crypto.mscapi (certificados de Windows).
#     Si al abrir falla con NoClassDefFoundError o ClassNotFoundException de una clase java.* / javax.*,
#     agregar aquí el módulo que falte.
MODULOS=java.base,java.desktop,java.logging,java.management,java.naming,java.prefs,java.security.jgss,java.sql,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.security.auth,jdk.unsupported
JLINK_OPCIONES="--strip-debug --no-man-pages --no-header-files --strip-native-commands --include-locales=en,es --compress=zip-6"

echo "[1/3] Compilando con Maven..."
mvn -q clean package

if [ "${1:-}" = "--solo-jar" ]; then
  echo
  echo "Listo: $DIST/validador-edocta.jar"
  exit 0
fi

echo "[2/3] Generando $APP_NAME.app con jpackage..."
mkdir -p target/icono
sips -s format icns packaging/icono.ico --out target/icono/icono.icns >/dev/null
jpackage --type app-image --name "$APP_NAME" --app-version "$APP_VERSION" \
  --input "$DIST" --main-jar validador-edocta.jar --main-class com.coppel.validador.App \
  --icon target/icono/icono.icns --add-modules "$MODULOS" --jlink-options "$JLINK_OPCIONES" \
  --dest "$SALIDA"

echo "[3/3] Copiando config.properties, bonificaciones.csv y logo.png junto al .app..."
# La app busca config.properties junto a ValidadorEdoCta.app; el csv y el logo, junto a config.properties.
cp config/config.properties "$SALIDA/"
cp src/main/resources/bonificaciones.csv "$SALIDA/"
if [ -f config/logo.png ]; then
  cp config/logo.png "$SALIDA/"
else
  echo "     Aviso: no hay config/logo.png, la app se mostrará sin logo."
fi

ditto -c -k --norsrc --noextattr --keepParent "$SALIDA" "target/installer/$APP_NAME.zip"

echo
echo "Listo: $SALIDA/$APP_NAME.app"
echo "Para compartir: target/installer/$APP_NAME.zip"
