#!/usr/bin/env bash
# Espera a que <url-base>/actuator/health responda "status":"UP", con reintentos.
#
# Uso:
#   scripts/esperar-health.sh <url-base>
#
# Lo usa el job "health de producción" de .github/workflows/ci.yml después de cada push a main, con la URL de la
# variable del repositorio RAILWAY_PUBLIC_URL. Si la URL viene vacía, avisa y sale con 0: el job se salta.
#
# Variables opcionales: ESPERA_INICIAL (segundos antes del primer intento, por defecto 60, para dar tiempo a que
# Railway despliegue), INTENTOS (por defecto 10) y PAUSA (segundos entre intentos, por defecto 30).
# Sale con 0 si llegó UP (o si no hay URL) y con 1 si no llegó tras todos los intentos.

set -uo pipefail

URL_BASE="${1:-}"
ESPERA_INICIAL="${ESPERA_INICIAL:-60}"
INTENTOS="${INTENTOS:-10}"
PAUSA="${PAUSA:-30}"

if [[ -z "$URL_BASE" ]]; then
	echo "::warning title=Health de producción omitido::No hay URL. Crea la variable del repositorio RAILWAY_PUBLIC_URL (Settings → Secrets and variables → Actions → Variables) con la URL pública del backend en Railway."
	exit 0
fi

URL="${URL_BASE%/}/actuator/health"
echo "Esperando $ESPERA_INICIAL s antes de revisar $URL"
sleep "$ESPERA_INICIAL"

for ((intento = 1; intento <= INTENTOS; intento++)); do
	respuesta="$(curl -sS --max-time 10 "$URL" 2>&1)" || true
	if [[ "$respuesta" == *'"status":"UP"'* ]]; then
		echo "Intento $intento/$INTENTOS: UP"
		exit 0
	fi
	echo "Intento $intento/$INTENTOS: todavía no responde UP (${respuesta:0:200})"
	if ((intento < INTENTOS)); then
		sleep "$PAUSA"
	fi
done

echo "::error title=Health de producción::$URL no respondió \"status\":\"UP\" en $INTENTOS intentos."
exit 1
