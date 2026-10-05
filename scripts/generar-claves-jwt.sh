#!/usr/bin/env sh
# Genera el par de claves RSA con el que el core firma (privada) y verifica (pública) los access
# tokens. Ejecutar UNA vez por ambiente (en Windows, desde Git Bash) y no versionar el resultado.
# Si se regeneran, todas las sesiones abiertas caducan (los usuarios vuelven a iniciar sesión).
#
#   sh scripts/generar-claves-jwt.sh            -> secrets/jwt/
#   sh scripts/generar-claves-jwt.sh otra/ruta
set -eu

DESTINO="${1:-$(dirname "$0")/../secrets/jwt}"
mkdir -p "$DESTINO"

if [ -f "$DESTINO/privateKey.pem" ]; then
  echo "Ya existe $DESTINO/privateKey.pem: no se sobrescribe (bórrela a mano si de verdad quiere rotarla)."
  exit 1
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$DESTINO/privateKey.pem"
openssl rsa -in "$DESTINO/privateKey.pem" -pubout -out "$DESTINO/publicKey.pem"
# El contenedor del core corre con un usuario sin privilegios: debe poder leerlas.
chmod 644 "$DESTINO/privateKey.pem" "$DESTINO/publicKey.pem"

echo "Claves generadas en $DESTINO (privateKey.pem / publicKey.pem)."
