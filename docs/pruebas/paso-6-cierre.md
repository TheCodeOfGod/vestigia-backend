# Paso 6 · Cierre y guías por cliente

Rama `feature/cierre`, comparada contra `feature/websocket`. Completa la documentación de Swagger (descripciones en español, ejemplos reales de petición y respuesta, errores con `codigo` y esquema `Bearer`), escribe una guía de integración independiente por cliente (`docs/integracion-unity.md`, `docs/integracion-flutter.md` y `docs/integracion-pwa.md`), completa `docs/tiempo-real.md`, agrega la página de prueba `/prueba-ws.html` y dos pruebas de integración que atan la documentación al backend real: `DocumentacionIT` y `JsonDocumentadoIT`. La API sigue en `/api/v1`, sin cambios de contrato. No hay migraciones nuevas.

## 1. Veredicto

✅ 234 pruebas, 0 fallos, 0 errores, 0 omitidas.

| Total | Unitarias | Integración | Fallos | Errores | Omitidas | Fecha | Commit | Rama |
|---:|---:|---:|---:|---:|---:|---|---|---|
| 234 | 124 | 110 | 0 | 0 | 0 | 2026-11-06 10:09 | `e91081c` | `feature/cierre` |

Las cifras son de `./mvnw -B clean verify` sobre `e91081c`, el último commit de código y documentación antes de esta evidencia. La rama partió de `02edd4d` con 226 pruebas; suma 8: cinco en `DocumentacionIT` y tres en `JsonDocumentadoIT`. El commit siguiente solo agrega esta evidencia y su fila en `docs/pruebas/README.md`.

## 2. Cómo reproducirlo

Con Docker corriendo (las pruebas `*IT` levantan `postgres:17-alpine` con Testcontainers):

```sh
git checkout feature/cierre
./mvnw clean verify
python3 scripts/resumen-pruebas.py               # secciones 1 y 4 de este archivo
python3 scripts/resumen-pruebas.py --solo-conteo # una línea
```

Para ver Swagger y la página de prueba, con el backend corriendo: `http://localhost:8080/swagger-ui/index.html`, `http://localhost:8080/v3/api-docs` y `http://localhost:8080/prueba-ws.html`.

**Las pruebas detectan lo que dicen cubrir.** Se rompió el código o un documento a propósito, se corrió la prueba indicada y se restauró el archivo:

| Mutación | Prueba | Resultado con la mutación |
|---|---|---|
| `@Operation(hidden = true)` en `PuebloController.listar` (una ruta real deja de estar en `/v3/api-docs`) | `DocumentacionIT#openApiTieneLasRutasReales` | ❌ el conjunto de rutas documentadas no es el de los controladores |
| La página de prueba guarda el token con `localStorage.setItem` | `DocumentacionIT#paginaDePrueba` | ❌ el HTML contiene `localStorage` |
| En `docs/integracion-unity.md`, la respuesta de `PUT escena` dice `{"escena": "pueblo"}` en lugar de `{"escena_actual": "pueblo"}` | `JsonDocumentadoIT#cadaRespuestaDocumentadaCoincideConUnaReal` | ❌ `docs/integracion-unity.md:406 (escena) $: campos de más [escena], campos que faltan [escena_actual]` |
| En `docs/integracion-flutter.md`, el `valor` de `coordenada_b` en el estado es el número `42` en lugar del texto `"+042"` | La misma | ❌ `docs/integracion-flutter.md:347 (estado) $.descubrimientos[5].datos.valor: una coordenada va en texto, no NUMBER` |

`JsonDocumentadoIT#laComparacionDetectaDiferencias` además fija, con dos JSON en el código, que la comparación detecta un tipo distinto, un campo de más, uno que falta, un elemento de arreglo distinto y una coordenada numérica.

**Ejecución literal de las guías.** Además de las pruebas, se corrieron con un script los comandos `curl` de la sección 4 de las guías de Unity y Flutter, copiados tal cual de los bloques ```` ```sh ````, contra el backend en `http://localhost:18080` (contenedor `vestigia-pg-6` en `55432`). Los tokens se copiaron de las respuestas, como pide cada guía. Encontró dos fallos en las guías, corregidos en `329ff37`:

| Guía | Falló | Corrección |
|---|---|---|
| Unity, paso 9 | Solo mostraba el `curl` de `pieza_1` y decía "repite con `pieza_2` y `pieza_3`". Al pie de la letra, `photo` no se habilitaba y los pasos 10, 12 y 14 daban `409` | Los tres comandos, explícitos |
| Flutter, sección 2.3 | Los comandos que simulan al juego estaban en un solo bloque que terminaba con `DELETE`: al pegarlo entero se cerraba la sala | Cinco bloques, cada uno con su momento |

Después de corregirlas, Unity terminó con `201`, `200` y `204` en todos sus pasos (sin ningún `4xx`), y Flutter recorrió `photo`, `lookout` y `observatory` hasta `resuelto` y cerró con `204`. Los fragmentos de TypeScript de la guía de la PWA se comprobaron con `tsc --noEmit` 6.0.3, en modo estricto, con los tipos de React 19 y Vite de la PWA: sin errores.

## 3. Requisito → pruebas

| Requisito del paso | Pruebas | Resultado |
|---|---|:-:|
| Swagger: todos los endpoints, verificados en `/swagger-ui/index.html` y `/v3/api-docs` | `DocumentacionIT` · /v3/api-docs documenta exactamente las rutas y métodos reales de los controladores de la API · Swagger UI se sirve en /swagger-ui/index.html · `AplicacionIT` · swaggerUiYOpenApiEstanDisponibles | ✅ |
| Swagger: descripciones en español, esquemas, ejemplos de petición y respuesta, errores con `codigo` | `DocumentacionIT` · Cada operación tiene resumen, descripción y ejemplos; cada error usa ErrorBody con codigo en su ejemplo | ✅ |
| Swagger: esquema de seguridad `Bearer` | `DocumentacionIT` · Las rutas con token declaran el esquema Bearer y las públicas no; el esquema es HTTP bearer | ✅ |
| `docs/tiempo-real.md`: cada mensaje con JSON de ejemplo y quién lo envía | `JsonDocumentadoIT` · Cada bloque JSON de respuesta de la documentación tiene los campos y tipos de una respuesta real (incluye los 7 bloques de `tiempo-real.md`) · Cada bloque JSON de api.md, tiempo-real.md y las tres guías está marcado como respuesta o como petición | ✅ |
| Guía de Unity con la estructura de 12 secciones y su contenido específico | Sin prueba automática de la estructura ni del contenido: es texto para personas. Los JSON de respuesta (25 bloques) los cubre `JsonDocumentadoIT`. Los `curl` de la sección 4 se ejecutaron al pie de la letra (sección 2). El desarrollador simulado lo lanza el orquestador | ✅ JSON · ⏳ simulado |
| Guía de Flutter con la estructura de 12 secciones y su contenido específico | Igual que Unity, con 15 bloques de respuesta | ✅ JSON · ⏳ simulado |
| Guía de la PWA con la estructura de 12 secciones, `VITE_API_URL`, CORS, tipos, hook y "Todavía no existe" | Igual, con 6 bloques de respuesta. El TypeScript se comprobó con `tsc` (sección 2) | ✅ JSON · ⏳ simulado |
| Cada bloque JSON de respuesta de `docs/api.md` y de las guías coincide con una respuesta real (campos y tipos) | `JsonDocumentadoIT` · las tres pruebas. 62 bloques de respuesta en 5 documentos | ✅ |
| Página de prueba en `src/main/resources/static/`, se sirve y no pone el token en la URL | `DocumentacionIT` · La página de prueba /prueba-ws.html se sirve como HTML, manda el token en auth y no lo pone en la URL ni en el navegador | ✅ |
| `README.md` con enlaces a las tres guías y sin enlaces a `docs/PLAN.md` ni a `CLAUDE.md` | Sin prueba automática. Revisado con `grep -n "PLAN.md\|CLAUDE.md" README.md`: sin resultados | ✅ revisado |
| `docs/PLAN.md` con los pasos marcados, sin pendientes resueltos ni O6 | Sin prueba automática. Paso 6 marcado; O4 (resuelto) y O6 quitados | ✅ revisado |
| `docs/modelo-datos.md` al día | Sin prueba automática. Agrega la tabla de claves de descubrimiento; el esquema no cambió | ✅ revisado |
| Verificación con un desarrollador simulado por guía (máximo 2 rondas) | Pendiente: la lanza el orquestador | ⏳ |

## 4. Lista completa de pruebas

| Tipo | Clase | Prueba | Tiempo (s) | Resultado |
|---|---|---|---:|---|
| unitaria | `config.ApiHttpConfigTest` | corsRechazaOrigenNoConfigurado | 0.022 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | corsPermiteOrigenConfigurado | 0.003 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | registraUnaLineaPorPeticionSinTokens(CapturedOutput) | 0.013 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | noRegistraRutasFueraDeApi(CapturedOutput) | 0.016 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Los tipos correctos se siguen leyendo, y en un objeto libre (Map) cada valor conserva su tipo JSON | 0.015 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Una clave repetida en el mismo objeto falla en la raíz, anidada, en un Map o en un objeto libre, aunque los dos valores coincidan | 0.002 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un número en un campo booleano no se convierte: falla, también "true" entre comillas | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un booleano en un campo numérico no se convierte: falla | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un número o un booleano en un campo de texto no se convierte a texto: falla | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | La misma clave en objetos distintos, hermanos o anidados, se sigue leyendo | 0.001 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Los errores sin código traen "codigo": null en 400, 401, 403, 404, 405, 409 y 500 | 0.017 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Los errores salen en JSON aunque el cliente pida otro formato: 406 con Accept XML y 409 con Accept HTML | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | rutaInexistenteDevuelve404ConCuerpoUniforme | 0.001 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | jsonMalformadoDevuelve400ConCuerpoUniforme | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | validacionDevuelve400ConDetallePorCampo | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | errorInesperadoDevuelve500SinDetallesInternos | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Un error con código lo lleva en codigo y sus detalles en details, con el mismo cuerpo uniforme | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | conflictoConservaSuCodigoYMensaje | 0.006 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin clave, con una clave que no es texto o con datos que no son un objeto JSON da 400 sin llamar al servicio | 0.013 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Unos datos de más de 4096 bytes en JSON dan 400 con detalle en datos, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin token da 401 y con el token de otra sala da 403, sin llamar al servicio | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave del servidor responde 403 con el cuerpo de error uniforme | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Repetir una clave responde 200 con el descubrimiento que ya existía | 0.004 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave vacía, con mayúsculas, guiones, acentos, espacios o de más de 40 caracteres da 400 con detalle | 0.006 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Un descubrimiento nuevo responde 201 con clave, datos, origen y creado_en | 0.002 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin datos, con datos null o con el aviso fuera de datos da 400 con details en datos y codigo null, sin llamar al servicio | 0.014 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin token da 401 y con el token de otra sala da 403, con codigo null y sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un aviso inválido para el puzzle responde 400 con el details del servicio y codigo null | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | PUT puzzle responde 200 con {puzzle, estado, datos} y pasa al servicio el id de la ruta y los datos | 0.004 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un puzzle bloqueado responde 409 con codigo puzzle_bloqueado en el cuerpo uniforme | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | datos que no es un objeto JSON llega al servicio tal cual, para que responda 400 con details | 0.004 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Los valores de datos llegan al servicio con su tipo JSON: "true" es texto, 1 es número y true es booleano | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | La app recibe 403 en PUT escena y en DELETE antes de validar el cuerpo, sin llamar al servicio | 0.026 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | pueblosPasaElFiltroDeDisponibles | 0.014 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeOtraSalaDa403 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin token da 401 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | unirseNoPideTokenYDevuelveElDeLaApp | 0.009 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearResponde201ConCodigoEstadoYToken | 0.027 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | DELETE de la sala responde 204 sin cuerpo y sin token da 401 | 0.004 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin escena o con escena vacía da 400 con detalle, sin llamar al servicio | 0.008 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDesconocidoDa401 | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearSinCodigoDa400ConDetalle | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeLaSalaDevuelveElEstadoEnSnakeCase | 0.009 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena responde 200 con escena_actual | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | El token de otra sala recibe 403 en PUT escena y DELETE, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearConPuebloOcupadoDa409ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoSinTokenDa401 | 0.003 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Los datos que caben en el límite, en bytes de su JSON compacto, son válidos; nulos también | 0.059 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Un byte más del límite es inválido, y los acentos cuentan como dos bytes | 0.031 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear devuelve la sala si sigue viva | 0.038 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Renovar pone ultima_actividad en ahora y expira_en en ahora más la expiración configurada | 0.000 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar el cierre de una sala finalizada guarda el evento y ultima_actividad, pero no renueva expira_en | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Los tipos de evento se guardan con su punto: sala.creada, app.unida, descubrimiento.registrado... | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar la expiración guarda y publica sala.expirada con origen servidor sin tocar ultima_actividad ni expira_en | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala finalizada, expirada o inexistente responde 401 | 0.000 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala viva cuyo expira_en ya pasó responde 401 aunque el job no la haya marcado | 0.000 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin guardar nada | 0.074 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | El descubrimiento se guarda en la sala del token con la clave recibida | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Repetir una clave devuelve el descubrimiento original sin guardar otro, sin evento y renovando la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un descubrimiento nuevo se guarda con el origen del rol, guarda su evento y luego recalcula los puzzles | 0.002 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Sin datos, el descubrimiento se guarda con un objeto vacío y origen juego si lo manda el juego | 0.000 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Las cuatro claves del servidor dan 403 con cualquier rol y no tocan la sala | 0.000 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un resultado del servidor se guarda con origen servidor y sus datos, guarda su evento y luego recalcula los puzzles | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | radio se habilita con pescador_dialogo_completo y no antes | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | photo se habilita solo con las tres piezas, lleguen en el orden que lleguen | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Solo un puzzle bloqueado pasa a disponible: los que están disponibles, en curso o resueltos no cambian | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular pasa a disponible los puzzles habilitados y guarda un evento puzzle.habilitado de origen servidor con {puzzle, estado, datos} por cada uno | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | observatory pide coordenada_a, coordenada_b, observatory_enabled y lookout resuelto; falta uno y no se habilita | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular sin requisitos nuevos no cambia nada ni guarda eventos | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | lookout pide photo resuelto y lookout_enabled; la bandera puede llegar antes o después | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | en_curso más iniciado y resuelto más cualquier aviso responden el estado actual, solo renuevan la sala y no guardan eventos ni descubrimientos | 0.028 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | El evento lleva el origen del rol que avisó: juego o app | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin leer ni tocar el puzzle | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Las coordenadas salen de la configuración, sin convertirse a número | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible más iniciado pasa a en_curso, guarda los datos y puzzle.iniciado con {puzzle, estado, datos} y el origen del rol, sin crear descubrimiento | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un puzzle bloqueado da 409 con codigo puzzle_bloqueado ante cualquier aviso válido y no cambia nada | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Cada puzzle crea su resultado con origen servidor: coordenada_a con AR "137", coordenada_b con DEC "+042" como texto, observatorio_visible y magnetar_localizado | 0.004 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible o en_curso más completada pasa a resuelto: guarda puzzle.resuelto y después crea el resultado | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Si falta la fila del puzzle, un estado imposible, falla con un mensaje que nombra la sala y el puzzle, sin escribir nada | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | radio solo acepta {sintonizada: true}: iniciado o completada dan 400; los puzzles de la app no aceptan sintonizada | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Solo vale un objeto con una clave aceptada y el booleano true: un valor que no es objeto, texto, número, false, null, claves extra, vacío o una clave ajena dan 400 con details en datos | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un id de puzzle desconocido da 400 con la lista de ids, también los viejos fotografia, mirador y observatorio y las variantes de mayúsculas o espacios | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearFueraDelCatalogoDa400SinTocarSalas | 0.065 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cambiar la escena la guarda y registra escena.cambiada con la anterior y la nueva | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | otraViolacionDeIntegridadNoSeDisfrazaDe409 | 0.003 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseSinSalaVivaDa404 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | listarPueblosMarcaOcupadosLosQueTienenSalaViva | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseEnlazaConUnSoloUpdateYRenuevaLaExpiracion | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearConPuebloOcupadoDa409 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Unirse guarda el evento app.unida con origen app sobre la sala enlazada | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cerrar la sala la pasa a finalizada y guarda sala.finalizada con origen juego | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Sin salas vencidas, el job no registra nada | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Una escena fuera de introduccion, pueblo, faro, observatorio y desenlace da 400 con la lista de válidas | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Crear la sala guarda el evento sala.creada con origen juego y el código en el payload | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Unirse a una sala con expira_en vencido que el job aún no marcó responde 404 con el mensaje de sala inexistente | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | El estado ordena los puzzles y reporta los dispositivos que da la presencia del WebSocket | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Si unirse pierde la carrera no guarda ningún evento | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Repetir la escena actual responde igual y solo renueva la sala, sin evento | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseASalaVivaQueYaTieneAppDa409 | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | La app no puede cerrar la sala: 403 sin tocarla | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | cadaSalaRecibeUnTokenDistinto | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearInsertaLosCuatroPuzzlesBloqueados | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | El job bloquea las salas vivas vencidas según el reloj, las marca expirada y guarda sala.expirada de cada una | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearNormalizaElCodigoYGuardaSoloElHashDelToken | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarDistingueElRolPorElHash | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarConTokenDesconocidoNoDaCredencial | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | La app no puede cambiar la escena: 403 sin tocar la sala | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Autenticar con el token de una sala viva con expira_en vencido no da credencial; justo en expira_en sí | 0.000 | ✅ pasó |
| unitaria | `ws.EmisorEventosTest` | Cada tipo de evento tiene su emisión: puzzle.actualizado y descubrimiento.nuevo a todos, escena.cambiada solo a la app, sala.cerrada cierra la sala, crear y unirse no emiten | 0.029 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al autenticarse, el cierre programado por el plazo de auth se cancela | 0.062 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Un auth rechazado no cancela el plazo: la sesión se cierra con 4001 y el plazo ya no hace nada | 0.001 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al abrir la conexión fija BLOCKING_SEND_TIMEOUT de Tomcat en la sesión con vestigia.ws.envio-limite, en milisegundos | 0.020 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al cerrarse una conexión sin autenticar, el cierre programado por el plazo de auth se cancela | 0.001 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | desconectar olvida una sesión rechazada, así que no queda guardada para siempre | 0.019 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Una sala que se cerró después de validar el token no registra la conexión: conectar devuelve false y no envía nada | 0.002 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | auth.ok es el primer mensaje de una conexión y los demás salen en el orden en que se encolaron | 0.003 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | La conexión reemplazada sigue autenticada hasta que Tomcat la cierra: un auth o un mensaje suyo no crean otra conexión ni otro close, y al desconectarse no avisa | 0.002 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Si la cola de una conexión pasa del límite de bytes, se descarta y la conexión se cierra con 4500 | 0.004 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Al desconectarse, lo que quedaba en la cola se descarta: no hay otro envío ni otro close sobre la sesión cerrada | 0.212 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Tras cerrarSala, la sesión sigue autenticada hasta que Tomcat la cierra: responder, conectar o cerrarSiNoSeAutentico no producen otro close ni otro envío | 0.004 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Si un envío falla, la conexión se cierra con 4500 y lo que seguía en su cola se descarta | 0.001 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Una sesión que el servidor ya decidió cerrar sin auth no se registra aunque su auth llegue después | 0.001 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Un envío que se bloquea no frena a nadie: enviar, dispositivos y la otra sala responden, y al soltarse sale todo en orden | 0.022 | ✅ pasó |
| integración | `AplicacionIT` | El programador que comparten el plazo de auth, los pings y el job de expiración tiene dos hilos | 0.001 | ✅ pasó |
| integración | `AplicacionIT` | rutaInexistenteBajoSalasDa404SinPedirToken | 0.002 | ✅ pasó |
| integración | `AplicacionIT` | elJobDeExpiracionEstaProgramadoCadaMinuto | 0.004 | ✅ pasó |
| integración | `AplicacionIT` | openApiDocumentaLosEndpointsDeSalasYElToken | 0.194 | ✅ pasó |
| integración | `AplicacionIT` | swaggerUiYOpenApiEstanDisponibles | 0.010 | ✅ pasó |
| integración | `AplicacionIT` | GET /pueblos con Accept: application/xml responde 406 con el error uniforme en JSON, no con un cuerpo vacío | 0.019 | ✅ pasó |
| integración | `AplicacionIT` | healthResponde200ConEstadoUp | 0.012 | ✅ pasó |
| integración | `CatalogoPueblosIT` | flywayAplicaV2YLosCodigosEstanEnLaTabla | 0.005 | ✅ pasó |
| integración | `CatalogoPueblosIT` | seedDeclara101CodigosValidosYSinDuplicados | 0.001 | ✅ pasó |
| integración | `DocumentacionIT` | La página de prueba /prueba-ws.html se sirve como HTML, manda el token en auth y no lo pone en la URL ni en el navegador | 0.003 | ✅ pasó |
| integración | `DocumentacionIT` | Cada operación tiene resumen, descripción y ejemplos; cada error usa ErrorBody con codigo en su ejemplo | 0.008 | ✅ pasó |
| integración | `DocumentacionIT` | Swagger UI se sirve en /swagger-ui/index.html | 0.002 | ✅ pasó |
| integración | `DocumentacionIT` | Las rutas con token declaran el esquema Bearer y las públicas no; el esquema es HTTP bearer | 0.005 | ✅ pasó |
| integración | `DocumentacionIT` | /v3/api-docs documenta exactamente las rutas y métodos reales de los controladores de la API | 0.005 | ✅ pasó |
| integración | `EscriturasSalaIT` | Con expira_en vencido y sin que corra el job, unirse da 404 y GET estado da 401 a los dos tokens; la sala no revive | 0.032 | ✅ pasó |
| integración | `EscriturasSalaIT` | pescador_dialogo_completo habilita radio y GET estado lo muestra disponible | 0.020 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala en esperando_app también se puede cerrar | 0.014 | ✅ pasó |
| integración | `EscriturasSalaIT` | El token del juego de otra sala recibe 403 en PUT escena y DELETE, y la sala no cambia | 0.019 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout_enabled que llega después de photo resuelto habilita lookout en la misma petición | 0.014 | ✅ pasó |
| integración | `EscriturasSalaIT` | Diez peticiones simultáneas con la misma clave dan un 201 y nueve 200, una sola fila y un solo evento | 0.027 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una escena inválida, vacía, de otro tipo o ausente da 400 y no cambia nada | 0.027 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cierra la sala: 204, queda finalizada sin renovar expira_en, guarda sala.finalizada y los dos tokens dan 401 | 0.030 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento, nuevo o repetido, renueva ultima_actividad y expira_en con la duración configurada | 0.018 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala con expira_en vencido que el job aún no marcó responde 401 a toda escritura y no revive | 0.026 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cerrar la sala libera el código: se puede crear otra sala con el mismo pueblo y la cerrada queda como historial | 0.020 | ✅ pasó |
| integración | `EscriturasSalaIT` | Crear y unirse guardan los eventos sala.creada (origen juego) y app.unida (origen app) | 0.011 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cada escritura deja su evento en orden: sala.creada, app.unida, descubrimiento.registrado, puzzle.habilitado, escena.cambiada y sala.finalizada | 0.025 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en | 0.016 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir la escena actual responde 200, renueva expira_en y no guarda otro escena.cambiada | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir una clave con otros datos responde 200 con los datos originales y no guarda otro evento | 0.018 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cerrar la sala y la sala sigue activa | 0.016 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un JSON con una clave repetida da 400 JSON malformado en crear sala, descubrimientos, escena y puzzles, sin escribir nada; los mismos cuerpos sin repetir siguen funcionando | 0.041 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout y observatory se habilitan por servicio cuando se resuelve el puzzle previo, aunque la bandera haya llegado antes | 0.027 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento nuevo responde 201, aparece en GET estado y guarda descubrimiento.registrado con el origen del rol | 0.020 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez | 0.026 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las cuatro claves del servidor dan 403 al juego y a la app y no se guardan | 0.026 | ✅ pasó |
| integración | `EscriturasSalaIT` | El job guarda sala.expirada con origen servidor y deja ultima_actividad en la última escritura | 0.017 | ✅ pasó |
| integración | `EscriturasSalaIT` | Unos datos que pasan de 4096 bytes en JSON dan 400 y no se guardan; justo en el límite responden 201 | 0.017 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cambiar la escena, con cuerpo válido, inválido o vacío, y la escena no cambia | 0.014 | ✅ pasó |
| integración | `EscriturasSalaIT` | DELETE simultáneo con nueve descubrimientos: los que pierden reciben 401 y no dejan filas ni eventos después del cierre | 0.065 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un número o booleano JSON donde va texto da 400 sin convertirse: clave, escena y código de pueblo | 0.029 | ✅ pasó |
| integración | `EsquemaIT` | salasTerminadasNoBloqueanUnaNuevaSalaViva | 0.005 | ✅ pasó |
| integración | `EsquemaIT` | indiceEventosPorSalaYTiempoExiste | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | noPuedeHaberDosSalasVivasConElMismoCodigo | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | catalogoRechazaCodigosConMinusculasAcentosOEnie | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaCodigoFueraDelCatalogo | 0.001 | ✅ pasó |
| integración | `EsquemaIT` | flywayAplicaV1YCreaLasCincoTablas | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | alFinalizarUnaSalaElCodigoSePuedeReclamarDeNuevo | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | carreraPorElMismoPuebloDejaUnaSolaSalaViva | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaEstadoInvalido | 0.002 | ✅ pasó |
| integración | `JsonDocumentadoIT` | Cada bloque JSON de respuesta de la documentación tiene los campos y tipos de una respuesta real | 0.091 | ✅ pasó |
| integración | `JsonDocumentadoIT` | La comparación de forma detecta un campo de más, uno que falta, un tipo distinto y una coordenada numérica | 0.002 | ✅ pasó |
| integración | `JsonDocumentadoIT` | Cada bloque JSON de api.md, tiempo-real.md y las tres guías está marcado como respuesta o como petición | 0.002 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos | 0.300 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | V3 renombra a inglés los puzzles de una sala creada con V2, conserva su estado y sus datos, y ese esquema rechaza fotografia | 0.158 | ✅ pasó |
| integración | `PuzzlesIT` | Con expira_en vencido y sin que corra el job, PUT puzzles responde 401 y no resuelve nada | 0.230 | ✅ pasó |
| integración | `PuzzlesIT` | Cada cambio de estado de un puzzle guarda {puzzle, estado, datos} y cada resultado guarda {clave, datos} con origen servidor | 0.095 | ✅ pasó |
| integración | `PuzzlesIT` | Los errores reales de PUT puzzles traen codigo null en 400, 401, 403, 404 y 405, y puzzle_bloqueado en el 409 | 0.057 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver es una sola transacción: si crear el resultado falla, el puzzle no queda resuelto, no se guarda ningún evento y la sala no se renueva | 0.093 | ✅ pasó |
| integración | `PuzzlesIT` | Un cliente no puede fabricar un resultado del servidor: POST de las cuatro claves da 403 y PUT de un puzzle bloqueado da 409, sin crear nada; el resultado real tiene origen servidor y los datos de la configuración | 0.093 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan antes que sus requisitos habilitan su puzzle en el PUT que completa el último requisito | 0.116 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan después de sus requisitos habilitan su puzzle en ese mismo POST | 0.121 | ✅ pasó |
| integración | `PuzzlesIT` | "+042" llega intacto como texto en GET estado y en la base (jsonb string), igual que "137" | 0.082 | ✅ pasó |
| integración | `PuzzlesIT` | Matriz de estados: bloqueado da 409; disponible+iniciado pasa a en_curso; en_curso+iniciado no cambia; en_curso+completada resuelve; resuelto+cualquier aviso no cambia ni duplica | 0.122 | ✅ pasó |
| integración | `PuzzlesIT` | observatory espera coordenada_a: con lookout resuelto y su bandera sigue bloqueado hasta que se resuelve radio, aunque llegue al final | 0.086 | ✅ pasó |
| integración | `PuzzlesIT` | Solo el booleano true: texto "true", 1, false, null, claves extra, objeto vacío, datos que no es objeto o datos ausente dan 400 con details en datos y no cambian nada; iniciado en radio también | 0.096 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver un puzzle actualiza ultima_actividad y renueva expira_en; repetir el aviso también renueva, sin guardar eventos | 0.037 | ✅ pasó |
| integración | `PuzzlesIT` | Un id de puzzle desconocido da 400 con la lista de ids, igual para los viejos fotografia, mirador y observatorio, y no toca la sala | 0.026 | ✅ pasó |
| integración | `PuzzlesIT` | Diez completada simultáneos sobre photo responden 200 y dejan una sola coordenada_b, un solo puzzle.resuelto y lookout habilitado una vez | 0.072 | ✅ pasó |
| integración | `PuzzlesIT` | La cadena completa por HTTP: pescador → radio → coordenada_a; tres piezas → photo → coordenada_b; lookout_enabled → lookout; observatory_enabled → observatory → magnetar_localizado | 0.105 | ✅ pasó |
| integración | `SalaIT` | crearDevuelveElTokenUnaVezYGuardaSoloSuHash | 0.015 | ✅ pasó |
| integración | `SalaIT` | carreraDeAppsPorUnirseDaUn200YSiete409 | 0.026 | ✅ pasó |
| integración | `SalaIT` | unirseSinSalaVivaDa404 | 0.008 | ✅ pasó |
| integración | `SalaIT` | flujoCrearUnirseEstado | 0.024 | ✅ pasó |
| integración | `SalaIT` | elJobNoTocaSalasFinalizadas | 0.031 | ✅ pasó |
| integración | `SalaIT` | unirseAceptaMinusculas | 0.013 | ✅ pasó |
| integración | `SalaIT` | pueblosReflejaLasSalasVivas | 0.016 | ✅ pasó |
| integración | `SalaIT` | estadoExigeElTokenDeEsaSala | 0.025 | ✅ pasó |
| integración | `SalaIT` | salaVencidaExpiraLiberaElNombreYSeConservaComoHistorial | 0.024 | ✅ pasó |
| integración | `SalaIT` | unirseRenuevaLaExpiracion | 0.012 | ✅ pasó |
| integración | `SalaIT` | carreraPorElMismoPuebloDaUn201YSiete409 | 0.011 | ✅ pasó |
| integración | `SalaIT` | crearFueraDelCatalogoDa400 | 0.003 | ✅ pasó |
| integración | `SalaIT` | crearAceptaMinusculasYEspacios | 0.007 | ✅ pasó |
| integración | `SalaIT` | crearInsertaLosCuatroPuzzlesBloqueados | 0.007 | ✅ pasó |
| integración | `SalaIT` | unirseDosVecesDa409 | 0.012 | ✅ pasó |
| integración | `SalaIT` | salaQueAunNoVenceSigueViva | 0.009 | ✅ pasó |
| integración | `repository.ModeloIT` | guardaYLeeSalaPuzzlesYDescubrimientosConJsonb | 0.032 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | Una conexión cuyo envío se queda bloqueado no frena GET estado, ni las escrituras de su sala, ni al otro dispositivo, ni a otra sala | 0.237 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | auth contra la expiración: si el job expira la sala entre validar el token y registrar la conexión, no queda conexión viva | 0.059 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | auth contra DELETE: si la sala se cierra entre validar el token y registrar la conexión, no queda conexión viva | 0.043 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | El job no expira una sala que una escritura simultánea renueva: espera su candado y vuelve a evaluar expira_en | 0.027 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Al cerrar la sala, los dos reciben sala.cerrada con estado finalizada y luego el servidor cierra con 1000 | 0.030 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | escena.cambiada llega solo a la app, con la escena nueva y la anterior; el juego no la recibe | 0.234 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Al expirar la sala, el job hace que los dos reciban sala.cerrada con estado expirada y cierra con 1000 | 0.043 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Los mensajes salen después del commit: nada mientras la transacción sigue abierta, y un GET estado pedido al recibir el mensaje ya ve el cambio aunque el commit tarde; un rollback no emite nada | 2.075 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Cada escritura REST de la cadena completa emite a los dos dispositivos su descubrimiento.nuevo y un puzzle.actualizado por cada cambio de estado, incluido bloqueado → disponible; otra sala no recibe nada | 0.326 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Las escrituras repetidas (descubrimiento, aviso de puzzle, misma escena) responden con éxito y no emiten nada | 0.055 | ✅ pasó |
| integración | `ws.WebSocketIT` | Antes de auth, otro tipo, JSON inválido o un mensaje binario cierran con 4000 | 0.008 | ✅ pasó |
| integración | `ws.WebSocketIT` | Ni los tokens ni Authorization aparecen en los logs durante auth, auth rechazada, errores y desconexión | 0.022 | ✅ pasó |
| integración | `ws.WebSocketIT` | Un token desconocido, vacío, que no es texto o ausente cierra con 4001 sin auth.ok | 0.007 | ✅ pasó |
| integración | `ws.WebSocketIT` | Origin: un navegador de un origen de CORS_ALLOWED_ORIGINS se conecta; otro origen recibe 403 en el handshake; sin Origin se conecta | 0.017 | ✅ pasó |
| integración | `ws.WebSocketIT` | Una segunda conexión del mismo rol reemplaza a la primera: la vieja cierra con 4009 y el otro rol no recibe desconectado | 0.350 | ✅ pasó |
| integración | `ws.WebSocketIT` | Presencia: cada conexión avisa dispositivo.conectado al otro rol, cada cierre dispositivo.desconectado, y GET estado lo refleja | 0.263 | ✅ pasó |
| integración | `ws.WebSocketIT` | auth con el token del juego o de la app responde auth.ok con su rol y la sala | 0.040 | ✅ pasó |
| integración | `ws.WebSocketIT` | El token de una sala cerrada o con expira_en vencido cierra con 4001, aunque el job no la haya marcado | 0.028 | ✅ pasó |
| integración | `ws.WebSocketIT` | El servidor manda pings a las conexiones autenticadas, programados cada 25 s | 0.037 | ✅ pasó |
| integración | `ws.WebSocketIT` | Ya autenticado, JSON inválido, binario, tipo desconocido, orientacion o un segundo auth responden error y la conexión sigue | 0.023 | ✅ pasó |
| integración | `ws.WebSocketIT` | Sin auth en 5 s el servidor cierra con 4008; una conexión que se autenticó a tiempo sigue abierta | 5.538 | ✅ pasó |
| integración | `ws.WebSocketIT` | Reconexión: la app vuelve con el mismo token, recibe auth.ok y no el mensaje que se perdió, y GET estado trae lo que pasó mientras no estaba | 0.271 | ✅ pasó |
| integración | `ws.WebSocketIT` | Un mensaje de 4096 caracteres recibe su error; uno de 4097 cierra con 1009 y avisa desconectado al otro rol | 0.028 | ✅ pasó |

## 5. Salida de `./mvnw clean verify`

Pruebas unitarias (`surefire`), línea 519 del log:

```
[INFO] Tests run: 124, Failures: 0, Errors: 0, Skipped: 0
```

Pruebas de integración (`failsafe`) y cierre:

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.vestigia.MigracionIdsPuzzlesIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.875 s -- in com.vestigia.MigracionIdsPuzzlesIT
[INFO] Running com.vestigia.PuzzlesIT
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.444 s -- in com.vestigia.PuzzlesIT
[INFO] Running com.vestigia.AplicacionIT
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.252 s -- in com.vestigia.AplicacionIT
[INFO] Running com.vestigia.repository.ModeloIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.040 s -- in com.vestigia.repository.ModeloIT
[INFO] Running com.vestigia.EsquemaIT
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.034 s -- in com.vestigia.EsquemaIT
[INFO] Running com.vestigia.CatalogoPueblosIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in com.vestigia.CatalogoPueblosIT
[INFO] Running com.vestigia.DocumentacionIT
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.028 s -- in com.vestigia.DocumentacionIT
[INFO] Running com.vestigia.SalaIT
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.251 s -- in com.vestigia.SalaIT
[INFO] Running com.vestigia.JsonDocumentadoIT
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.102 s -- in com.vestigia.JsonDocumentadoIT
[INFO] Running com.vestigia.EscriturasSalaIT
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.648 s -- in com.vestigia.EscriturasSalaIT
[INFO] Running com.vestigia.ws.WebSocketIT
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.651 s -- in com.vestigia.ws.WebSocketIT
[INFO] Running com.vestigia.ws.EmisionWebSocketIT
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.775 s -- in com.vestigia.ws.EmisionWebSocketIT
[INFO] Running com.vestigia.ws.ConcurrenciaWebSocketIT
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.373 s -- in com.vestigia.ws.ConcurrenciaWebSocketIT
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 110, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] 
[INFO] --- failsafe:3.5.6:verify (default) @ vestigia ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  25.898 s
[INFO] Finished at: 2026-11-06T10:08:48-06:00
[INFO] ------------------------------------------------------------------------
```

## 6. Pruebas adversariales de la API

pendiente: las completa el orquestador.

## 7. Revisiones independientes

pendiente: las completa el orquestador.

## 8. Lo que no se probó y por qué

| Qué | Por qué |
|---|---|
| Los fragmentos de C# de `docs/integracion-unity.md` | No hay Unity ni `dotnet` en este equipo. Están marcados **no compilados** en la guía |
| Los fragmentos de Dart de `docs/integracion-flutter.md` | No hay Flutter ni `dart` en este equipo. Están marcados **no compilados** |
| La regla de caché de `vite.config.ts` de la guía de la PWA | Es un fragmento de configuración, no un archivo completo. La guía lo dice |
| La página de prueba en un navegador real | La prueba revisa que se sirva y su código; no abre un navegador. Se cargó con `curl` (`200 text/html`) contra el backend local |
| Que las guías estén "en peras y manzanas" y sean independientes | Es juicio humano. Lo comprueban los desarrolladores simulados del orquestador |
| `GameProgress.cs` | No está en este equipo. La tabla de banderas de la guía de Unity está marcada "nombres tomados del plan; no verificados contra el código de Unity" |
| Los bloques JSON marcados `<!-- peticion -->` (lo que mandan los clientes) | La prueba compara solo respuestas, como pide la especificación. Las peticiones de la sección 4 de Unity y Flutter se ejecutaron al pie de la letra (sección 2) |
