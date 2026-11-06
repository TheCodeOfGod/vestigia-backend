#!/usr/bin/env bash
# Prueba de humo de punta a punta del backend de Vestigia.
#
# Uso:
#   scripts/smoke-test.sh <url-base> [pueblo]
#
#   scripts/smoke-test.sh http://localhost:8080
#   scripts/smoke-test.sh https://<tu-servicio>.up.railway.app BACALAR
#
# Recorre la partida completa con curl y jq: health, crear sala, unirse, escenas, banderas del juego, los cuatro
# puzzles, estado y cierre. Sin pueblo, toma uno libre de GET /api/v1/pueblos?disponibles=true.
#
# Si un paso falla, se detiene, dice cuál y sale con código 1. Pase lo que pase, al salir cierra la sala que creó
# (DELETE con el token del juego), para no dejar un pueblo ocupado. La sala queda como historial en estado finalizada.
# Códigos de salida: 0 todo bien, 1 falló un paso, 2 uso incorrecto, 3 falta curl o jq, 129, 130 o 143 se
# interrumpió (HUP, INT o TERM).
#
# Nunca imprime los tokens: en la salida aparecen como <token>.

# Sin -e a propósito: cada fallo pasa por fallar(), que nombra lo esperado; -e cortaría sin decir por qué.
set -uo pipefail

if [[ $# -lt 1 || $# -gt 2 || "$1" == "-h" || "$1" == "--help" ]]; then
	sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
	exit 2
fi

for herramienta in curl jq; do
	if ! command -v "$herramienta" >/dev/null 2>&1; then
		echo "Falta '$herramienta'. Instálalo (macOS: brew install $herramienta; Debian/Ubuntu: sudo apt-get install $herramienta) y vuelve a correr el script." >&2
		exit 3
	fi
done

BASE="${1%/}"
PUEBLO="${2:-}"
API="$BASE/api/v1"
TIEMPO_MAX="${SMOKE_TIEMPO_MAX:-20}"

CUERPO_TMP="$(mktemp)"
TOKEN_JUEGO=""
TOKEN_APP=""
CODIGO=""
SALA_CERRADA=0
PASO=0
STATUS=""
CUERPO=""

# Oculta el valor de cualquier campo "token" del texto: con jq si es JSON y con sed si no (una página HTML del proxy).
ocultar_tokens() {
	local limpio
	if limpio="$(printf '%s' "$1" | jq -c 'walk(if type == "object" and has("token") then .token = "<token>" else . end)' 2>/dev/null)"; then
		printf '%s' "$limpio"
	else
		printf '%s' "$1" | sed -E 's/"token"[[:space:]]*:[[:space:]]*"[^"]*"/"token":"<token>"/g'
	fi
}

fallar() {
	echo "  ✗ $1" >&2
	if [[ -n "$STATUS" ]]; then
		echo "    Respuesta $STATUS: $(ocultar_tokens "$CUERPO" | tr '\n' ' ' | cut -c1-600)" >&2
	fi
	exit 1
}

paso() {
	PASO=$((PASO + 1))
	printf '%2d. %s\n' "$PASO" "$1"
}

ok() {
	echo "    ✓ $1"
}

# peticion METODO RUTA TOKEN CUERPO_JSON: deja el código HTTP en STATUS y la respuesta en CUERPO.
peticion() {
	local metodo="$1" url="$2" token="$3" datos="$4"
	local args=(-sS -o "$CUERPO_TMP" -w '%{http_code}' --max-time "$TIEMPO_MAX" -X "$metodo" -H 'Accept: application/json')
	if [[ -n "$token" ]]; then
		args+=(-H "Authorization: Bearer $token")
	fi
	if [[ -n "$datos" ]]; then
		args+=(-H 'Content-Type: application/json' --data "$datos")
	fi
	: >"$CUERPO_TMP"
	if ! STATUS="$(curl "${args[@]}" "$url")"; then
		STATUS=""
		CUERPO=""
		fallar "$metodo $url: no hubo respuesta (¿la URL es correcta y el servicio está arriba?)"
	fi
	CUERPO="$(cat "$CUERPO_TMP")"
}

# espera CODIGO_HTTP: falla si la última respuesta no tiene ese código.
espera() {
	[[ "$STATUS" == "$1" ]] || fallar "se esperaba $1 y llegó $STATUS"
}

# comprueba FILTRO_JQ DESCRIPCION: falla si el filtro no da true sobre la última respuesta.
comprueba() {
	local resultado
	resultado="$(printf '%s' "$CUERPO" | jq -e "$1" 2>/dev/null)" || fallar "no se cumple: $2"
	[[ "$resultado" == "true" ]] || fallar "no se cumple: $2"
}

cerrar_al_salir() {
	local salida=$?
	trap - EXIT HUP INT TERM
	if [[ -n "$TOKEN_JUEGO" && "$SALA_CERRADA" -eq 0 ]]; then
		local status
		status="$(curl -sS -o /dev/null -w '%{http_code}' --max-time "$TIEMPO_MAX" -X DELETE \
			-H "Authorization: Bearer $TOKEN_JUEGO" "$API/salas/$CODIGO" 2>/dev/null)" || status="sin respuesta"
		if [[ "$status" == "204" ]]; then
			echo "Limpieza: la sala $CODIGO se cerró (DELETE 204) para no dejar el pueblo ocupado." >&2
		else
			echo "Limpieza: no se pudo cerrar la sala $CODIGO (DELETE $status). Expira sola tras 2 horas sin actividad." >&2
			[[ $salida -eq 0 ]] && salida=1
		fi
	fi
	rm -f "$CUERPO_TMP"
	if [[ $salida -eq 0 ]]; then
		echo "RESULTADO: ✅ la cadena completa pasó contra $BASE"
	elif [[ $salida -gt 128 ]]; then
		echo "RESULTADO: ❌ se interrumpió en el paso $PASO contra $BASE" >&2
	else
		echo "RESULTADO: ❌ falló en el paso $PASO contra $BASE" >&2
	fi
	exit "$salida"
}
trap cerrar_al_salir EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

echo "Prueba de humo contra $BASE"

paso "GET /actuator/health"
peticion GET "$BASE/actuator/health" "" ""
espera 200
comprueba '.status == "UP"' 'status es UP'
ok "UP"

if [[ -z "$PUEBLO" ]]; then
	paso "GET /pueblos?disponibles=true (elige un pueblo libre)"
	peticion GET "$API/pueblos?disponibles=true" "" ""
	espera 200
	comprueba 'type == "array" and length > 0 and all(.[]; .libre == true)' 'hay pueblos libres'
	PUEBLO="$(printf '%s' "$CUERPO" | jq -r --argjson i "$RANDOM" '.[$i % length].codigo')"
	ok "$(printf '%s' "$CUERPO" | jq length) libres; se usa $PUEBLO"
fi

paso "POST /salas (juego crea la sala $PUEBLO)"
peticion POST "$API/salas" "" "$(jq -nc --arg c "$PUEBLO" '{codigo: $c}')"
espera 201
# El token se guarda antes de cualquier comprobación: si una falla, la limpieza del trap ya puede cerrar la sala.
TOKEN_JUEGO="$(printf '%s' "$CUERPO" | jq -r '.token // empty | strings' 2>/dev/null)"
CODIGO="$(printf '%s' "$CUERPO" | jq -r '.codigo // empty | strings' 2>/dev/null)"
[[ -n "$CODIGO" ]] || CODIGO="$(printf '%s' "$PUEBLO" | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')"
comprueba '.estado == "esperando_app" and (.token | type == "string" and length == 43)' 'sala en esperando_app con token'
ok "sala $CODIGO, token del juego recibido"

paso "POST /salas/$CODIGO/unirse (app)"
peticion POST "$API/salas/$CODIGO/unirse" "" ""
espera 200
comprueba '.token | type == "string" and length == 43' 'token de la app'
TOKEN_APP="$(printf '%s' "$CUERPO" | jq -r .token)"
ok "token de la app recibido"

paso "GET /salas/$CODIGO/estado (app)"
peticion GET "$API/salas/$CODIGO/estado" "$TOKEN_APP" ""
espera 200
comprueba '.estado == "activa" and .escena_actual == "introduccion"' 'sala activa en introduccion'
comprueba '[.puzzles[].puzzle] == ["radio","photo","lookout","observatory"] and all(.puzzles[]; .estado == "bloqueado")' 'cuatro puzzles bloqueados'
ok "activa, cuatro puzzles bloqueados"

escena() {
	paso "PUT /salas/$CODIGO/escena → $1 (juego)"
	peticion PUT "$API/salas/$CODIGO/escena" "$TOKEN_JUEGO" "{\"escena\":\"$1\"}"
	espera 200
	comprueba ".escena_actual == \"$1\"" "escena $1"
	ok "escena_actual = $1"
}

descubrimiento() {
	paso "POST /salas/$CODIGO/descubrimientos $1 (juego)"
	peticion POST "$API/salas/$CODIGO/descubrimientos" "$TOKEN_JUEGO" "{\"clave\":\"$1\",\"datos\":{\"origen_prueba\":\"smoke-test\"}}"
	espera 201
	comprueba ".clave == \"$1\" and .origen == \"juego\"" "descubrimiento $1 del juego"
	ok "registrado"
}

# puzzle ID AVISO TOKEN ROL ESTADO_ESPERADO
puzzle() {
	paso "PUT /salas/$CODIGO/puzzles/$1 {$2: true} ($4)"
	peticion PUT "$API/salas/$CODIGO/puzzles/$1" "$3" "{\"datos\":{\"$2\":true}}"
	espera 200
	comprueba ".puzzle == \"$1\" and .estado == \"$5\"" "$1 en $5"
	ok "$1 = $5"
}

estado_puzzle() {
	peticion GET "$API/salas/$CODIGO/estado" "$TOKEN_JUEGO" ""
	espera 200
	comprueba "(.puzzles[] | select(.puzzle == \"$1\") | .estado) == \"$2\"" "$1 en $2"
}

escena pueblo
descubrimiento pescador_dialogo_completo
estado_puzzle radio disponible
ok "radio disponible"
puzzle radio sintonizada "$TOKEN_JUEGO" juego resuelto

descubrimiento pieza_1
descubrimiento pieza_2
descubrimiento pieza_3
estado_puzzle photo disponible
ok "photo disponible"
puzzle photo iniciado "$TOKEN_APP" app en_curso
puzzle photo completada "$TOKEN_APP" app resuelto

escena faro
descubrimiento lookout_enabled
estado_puzzle lookout disponible
ok "lookout disponible"
puzzle lookout iniciado "$TOKEN_APP" app en_curso
puzzle lookout completada "$TOKEN_APP" app resuelto

escena observatorio
descubrimiento observatory_enabled
estado_puzzle observatory disponible
ok "observatory disponible"
puzzle observatory iniciado "$TOKEN_APP" app en_curso
puzzle observatory completada "$TOKEN_APP" app resuelto

escena desenlace

paso "GET /salas/$CODIGO/estado (juego): resultados del servidor"
peticion GET "$API/salas/$CODIGO/estado" "$TOKEN_JUEGO" ""
espera 200
comprueba 'all(.puzzles[]; .estado == "resuelto")' 'los cuatro puzzles resueltos'
comprueba '.escena_actual == "desenlace"' 'escena desenlace'
comprueba '[.descubrimientos[] | select(.origen == "servidor") | .clave] == ["coordenada_a","coordenada_b","observatorio_visible","magnetar_localizado"]' 'los cuatro resultados del servidor, en orden'
comprueba '(.descubrimientos[] | select(.clave == "coordenada_a") | .datos) as $a | $a.tipo == "ascension_recta" and ($a.valor | type == "string" and length > 0)' 'coordenada_a es ascensión recta en texto'
comprueba '(.descubrimientos[] | select(.clave == "coordenada_b") | .datos) as $b | $b.tipo == "declinacion" and ($b.valor | type == "string" and length > 0)' 'coordenada_b es declinación en texto'
ok "4 puzzles resueltos; AR $(printf '%s' "$CUERPO" | jq -r '.descubrimientos[] | select(.clave == "coordenada_a") | .datos.valor'), DEC $(printf '%s' "$CUERPO" | jq -r '.descubrimientos[] | select(.clave == "coordenada_b") | .datos.valor'); magnetar_localizado"

paso "DELETE /salas/$CODIGO (juego cierra la sala)"
peticion DELETE "$API/salas/$CODIGO" "$TOKEN_JUEGO" ""
espera 204
SALA_CERRADA=1
ok "204"

paso "GET /salas/$CODIGO/estado con el token del juego tras cerrar"
peticion GET "$API/salas/$CODIGO/estado" "$TOKEN_JUEGO" ""
espera 401
ok "401: el token ya no sirve"

paso "GET /pueblos?disponibles=true: $CODIGO vuelve a estar libre"
peticion GET "$API/pueblos?disponibles=true" "" ""
espera 200
comprueba "any(.[]; .codigo == \"$CODIGO\")" "$CODIGO libre"
ok "$CODIGO libre"
