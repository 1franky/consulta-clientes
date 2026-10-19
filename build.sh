#!/usr/bin/env bash
# Compila y ejecuta la aplicación en macOS (para pruebas; el .exe se genera con build.bat en Windows).
# Uso:
#   ./build.sh              compila y abre la aplicación
#   ./build.sh --solo-jar   solo compila
set -euo pipefail

cd "$(dirname "$0")"

SALIDA=target/dist

echo "[1/3] Compilando con Maven..."
mvn -q clean package

echo "[2/3] Copiando config.properties junto al .jar..."
cp config/config.properties "$SALIDA/"
if [ -f config/logo.png ]; then
  cp config/logo.png "$SALIDA/"
else
  echo "     Aviso: no hay config/logo.png, la app se mostrará sin logo."
fi

echo
echo "Listo: $SALIDA/consulta-clientes.jar"

if [ "${1:-}" = "--solo-jar" ]; then
  exit 0
fi

echo "[3/3] Abriendo la aplicación..."
# La app busca config.properties en la carpeta de trabajo actual
cd "$SALIDA"
java -jar consulta-clientes.jar
