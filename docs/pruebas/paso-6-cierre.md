# Paso 6 · Cierre y guías por cliente

Rama `feature/cierre`, comparada contra `feature/websocket`. Completa la documentación de Swagger (descripciones en español, ejemplos reales de petición y respuesta, errores con `codigo` y esquema `Bearer`), escribe una guía de integración independiente por cliente (`docs/integracion-unity.md`, `docs/integracion-flutter.md` y `docs/integracion-pwa.md`), completa `docs/tiempo-real.md`, agrega la página de prueba `/prueba-ws.html` y dos pruebas de integración que atan la documentación al backend real: `DocumentacionIT` y `JsonDocumentadoIT`. La API sigue en `/api/v1`, sin cambios de contrato. No hay migraciones nuevas.

## 1. Veredicto

✅ 235 pruebas, 0 fallos, 0 errores, 0 omitidas.

| Total | Unitarias | Integración | Fallos | Errores | Omitidas | Fecha | Commit | Rama |
|---:|---:|---:|---:|---:|---:|---|---|---|
| 235 | 124 | 111 | 0 | 0 | 0 | 2026-11-06 10:42 | `11d1c0d` | `feature/cierre` |

Las cifras son de `./mvnw -B clean verify` sobre `11d1c0d`, después de la ronda 2 de revisión. El último commit que toca código o pruebas es `3d373c9`; los que siguen hasta `11d1c0d` solo cambian documentación (guías, `api.md`, `PLAN.md` y esta evidencia), que `JsonDocumentadoIT` también lee. La rama partió de `02edd4d` con 226 pruebas. La primera entrega (`e91081c`) sumó 8 (cinco en `DocumentacionIT` y tres en `JsonDocumentadoIT`) y la ronda 1 sumó una: `JsonDocumentadoIT` compara también los ejemplos de Swagger. El commit siguiente solo actualiza esta evidencia y `docs/pruebas/README.md`.

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

Mutaciones de la ronda 1 de revisión, sobre las reglas nuevas:

| Mutación | Prueba | Resultado con la mutación |
|---|---|---|
| El ejemplo de Swagger de `PUT puzzles` dice `"estado": "terminado"` | `JsonDocumentadoIT#cadaEjemploDeSwaggerCoincideConUnaReal` | ❌ `PUT /api/v1/salas/{codigo}/puzzles/{puzzle} 200 (puzzle) $.estado: 'terminado' no es un valor válido` |
| Al final de `docs/integracion-pwa.md`, un bloque ```` ```JSON ```` y otro sin lenguaje que empieza con `[`, los dos sin marca | `JsonDocumentadoIT#todosLosBloquesEstanMarcados` | ❌ `["docs/integracion-pwa.md:598", "docs/integracion-pwa.md:602"]` |

`JsonDocumentadoIT#laComparacionDetectaDiferencias` además fija, con dos JSON en el código, que la comparación detecta un tipo distinto, un campo de más, uno que falta, un elemento de arreglo distinto, una coordenada numérica, un aviso (`completada`) que no es booleano, un `null` fuera de `codigo` y valores de `estado` y `puzzle` que no existen en los enums; y que acepta `null` contra texto en `codigo`.

**Ejecución literal de las guías.** Además de las pruebas, se corrieron con un script los comandos `curl` de la sección 4 de las guías de Unity y Flutter, copiados tal cual de los bloques ```` ```sh ````, contra el backend en `http://localhost:18080` (contenedor `vestigia-pg-6` en `55432`). Los tokens se copiaron de las respuestas, como pide cada guía. Encontró dos fallos en las guías, corregidos en `329ff37`:

| Guía | Falló | Corrección |
|---|---|---|
| Unity, paso 9 | Solo mostraba el `curl` de `pieza_1` y decía "repite con `pieza_2` y `pieza_3`". Al pie de la letra, `photo` no se habilitaba y los pasos 10, 12 y 14 daban `409` | Los tres comandos, explícitos |
| Flutter, sección 2.3 | Los comandos que simulan al juego estaban en un solo bloque que terminaba con `DELETE`: al pegarlo entero se cerraba la sala | Cinco bloques, cada uno con su momento |

Después de corregirlas, Unity terminó con `201`, `200` y `204` en todos sus pasos (sin ningún `4xx`), y Flutter recorrió `photo`, `lookout` y `observatory` hasta `resuelto` y cerró con `204`.

**Repetición tras la ronda 1.** Se volvió a correr igual, con las guías corregidas, contra un backend propio en `http://localhost:18081` (contenedor `vestigia-pg-6b` en `55433`, borrado al terminar). Unity: 20 peticiones, incluidas las de la app de su sección 2.3 (`unirse`, y `iniciado` y `completada` en `photo`, `lookout` y `observatory`); todas `200`, `201` o `204`, ninguna `4xx`. Flutter: crear sala, piezas, unirse, `iniciado` y `completada` en los tres puzzles, las banderas, la radio, la escena y el `DELETE` (`204`); el `GET estado` posterior dio `401`, como dice la guía. Los dos tokens midieron 43 caracteres. Los fragmentos de TypeScript de la guía de la PWA se comprobaron con `tsc --noEmit` 6.0.3, en modo estricto, con los tipos de React 19 y Vite de la PWA: sin errores.

## 3. Requisito → pruebas

| Requisito del paso | Pruebas | Resultado |
|---|---|:-:|
| Swagger: todos los endpoints, verificados en `/swagger-ui/index.html` y `/v3/api-docs` | `DocumentacionIT` · /v3/api-docs documenta exactamente las rutas y métodos reales de los controladores de la API · Swagger UI se sirve en /swagger-ui/index.html · `AplicacionIT` · swaggerUiYOpenApiEstanDisponibles | ✅ |
| Swagger: descripciones en español, esquemas, ejemplos de petición y respuesta, errores con `codigo` | `DocumentacionIT` · Cada operación tiene resumen, descripción y ejemplos; cada error usa ErrorBody con codigo en su ejemplo | ✅ |
| Swagger: esquema de seguridad `Bearer` | `DocumentacionIT` · Las rutas con token declaran el esquema Bearer y las públicas no; el esquema es HTTP bearer | ✅ |
| `docs/tiempo-real.md`: cada mensaje con JSON de ejemplo y quién lo envía | `JsonDocumentadoIT` · Cada bloque JSON de respuesta de la documentación tiene los campos y tipos de una respuesta real (incluye los 7 bloques de `tiempo-real.md`) · Cada bloque JSON de api.md, tiempo-real.md y las tres guías está marcado como respuesta o como petición | ✅ |
| Guía de Unity con la estructura de 12 secciones y su contenido específico | Sin prueba automática de la estructura ni del contenido: es texto para personas. Los JSON de respuesta (25 bloques) los cubre `JsonDocumentadoIT`. Los `curl` de la sección 4 se ejecutaron al pie de la letra (sección 2). El desarrollador simulado la siguió completa (sección 6) | ✅ |
| Guía de Flutter con la estructura de 12 secciones y su contenido específico | Igual que Unity, con 17 bloques de respuesta | ✅ |
| Guía de la PWA con la estructura de 12 secciones, `VITE_API_URL`, CORS, tipos, hook y "Todavía no existe" | Igual, con 6 bloques de respuesta. El TypeScript se comprobó con `tsc` (sección 2) | ✅ |
| Cada bloque JSON de respuesta de `docs/api.md` y de las guías coincide con una respuesta real (campos, tipos y valores de los enums) | `JsonDocumentadoIT` · Cada bloque JSON de respuesta de la documentación tiene los campos y tipos de una respuesta real · Cada bloque JSON… está marcado como respuesta o como petición · La comparación detecta… 64 bloques de respuesta en 5 documentos | ✅ |
| Los ejemplos de Swagger coinciden con respuestas reales (hallazgo B4) | `JsonDocumentadoIT` · Cada ejemplo de respuesta de Swagger (/v3/api-docs) tiene los campos y tipos de una respuesta real | ✅ |
| Página de prueba en `src/main/resources/static/`, se sirve y no pone el token en la URL | `DocumentacionIT` · La página de prueba /prueba-ws.html se sirve como HTML, manda el token en auth y no lo pone en la URL ni en el navegador | ✅ |
| `README.md` con enlaces a las tres guías y sin enlaces a `docs/PLAN.md` ni a `CLAUDE.md` | Sin prueba automática. Revisado con `grep -n "PLAN.md\|CLAUDE.md" README.md`: sin resultados | ✅ revisado |
| `docs/PLAN.md` con los pasos marcados, sin pendientes resueltos ni O6 | Sin prueba automática. Paso 6 marcado; O4 (resuelto) y O6 quitados | ✅ revisado |
| `docs/modelo-datos.md` al día | Sin prueba automática. Agrega la tabla de claves de descubrimiento; el esquema no cambió | ✅ revisado |
| Verificación con un desarrollador simulado por guía (máximo 2 rondas) | Ronda 1: los tres siguieron su guía completa contra el backend real; sus hallazgos están en la sección 6, todos corregidos | ✅ |

## 4. Lista completa de pruebas

| Tipo | Clase | Prueba | Tiempo (s) | Resultado |
|---|---|---|---:|---|
| unitaria | `config.ApiHttpConfigTest` | corsRechazaOrigenNoConfigurado | 0.023 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | corsPermiteOrigenConfigurado | 0.004 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | registraUnaLineaPorPeticionSinTokens(CapturedOutput) | 0.013 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | noRegistraRutasFueraDeApi(CapturedOutput) | 0.018 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Los tipos correctos se siguen leyendo, y en un objeto libre (Map) cada valor conserva su tipo JSON | 0.016 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Una clave repetida en el mismo objeto falla en la raíz, anidada, en un Map o en un objeto libre, aunque los dos valores coincidan | 0.002 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un número en un campo booleano no se convierte: falla, también "true" entre comillas | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un booleano en un campo numérico no se convierte: falla | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un número o un booleano en un campo de texto no se convierte a texto: falla | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | La misma clave en objetos distintos, hermanos o anidados, se sigue leyendo | 0.000 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Los errores sin código traen "codigo": null en 400, 401, 403, 404, 405, 409 y 500 | 0.018 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Los errores salen en JSON aunque el cliente pida otro formato: 406 con Accept XML y 409 con Accept HTML | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | rutaInexistenteDevuelve404ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | jsonMalformadoDevuelve400ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | validacionDevuelve400ConDetallePorCampo | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | errorInesperadoDevuelve500SinDetallesInternos | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Un error con código lo lleva en codigo y sus detalles en details, con el mismo cuerpo uniforme | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | conflictoConservaSuCodigoYMensaje | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin clave, con una clave que no es texto o con datos que no son un objeto JSON da 400 sin llamar al servicio | 0.015 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Unos datos de más de 4096 bytes en JSON dan 400 con detalle en datos, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin token da 401 y con el token de otra sala da 403, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave del servidor responde 403 con el cuerpo de error uniforme | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Repetir una clave responde 200 con el descubrimiento que ya existía | 0.004 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave vacía, con mayúsculas, guiones, acentos, espacios o de más de 40 caracteres da 400 con detalle | 0.011 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Un descubrimiento nuevo responde 201 con clave, datos, origen y creado_en | 0.002 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin datos, con datos null o con el aviso fuera de datos da 400 con details en datos y codigo null, sin llamar al servicio | 0.014 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin token da 401 y con el token de otra sala da 403, con codigo null y sin llamar al servicio | 0.002 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un aviso inválido para el puzzle responde 400 con el details del servicio y codigo null | 0.004 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | PUT puzzle responde 200 con {puzzle, estado, datos} y pasa al servicio el id de la ruta y los datos | 0.004 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un puzzle bloqueado responde 409 con codigo puzzle_bloqueado en el cuerpo uniforme | 0.002 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | datos que no es un objeto JSON llega al servicio tal cual, para que responda 400 con details | 0.005 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Los valores de datos llegan al servicio con su tipo JSON: "true" es texto, 1 es número y true es booleano | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | La app recibe 403 en PUT escena y en DELETE antes de validar el cuerpo, sin llamar al servicio | 0.026 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | pueblosPasaElFiltroDeDisponibles | 0.014 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeOtraSalaDa403 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin token da 401 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | unirseNoPideTokenYDevuelveElDeLaApp | 0.010 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearResponde201ConCodigoEstadoYToken | 0.030 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | DELETE de la sala responde 204 sin cuerpo y sin token da 401 | 0.004 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin escena o con escena vacía da 400 con detalle, sin llamar al servicio | 0.009 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDesconocidoDa401 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearSinCodigoDa400ConDetalle | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeLaSalaDevuelveElEstadoEnSnakeCase | 0.010 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena responde 200 con escena_actual | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | El token de otra sala recibe 403 en PUT escena y DELETE, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearConPuebloOcupadoDa409ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoSinTokenDa401 | 0.003 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Los datos que caben en el límite, en bytes de su JSON compacto, son válidos; nulos también | 0.059 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Un byte más del límite es inválido, y los acentos cuentan como dos bytes | 0.032 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear devuelve la sala si sigue viva | 0.042 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Renovar pone ultima_actividad en ahora y expira_en en ahora más la expiración configurada | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar el cierre de una sala finalizada guarda el evento y ultima_actividad, pero no renueva expira_en | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Los tipos de evento se guardan con su punto: sala.creada, app.unida, descubrimiento.registrado... | 0.000 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar la expiración guarda y publica sala.expirada con origen servidor sin tocar ultima_actividad ni expira_en | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala finalizada, expirada o inexistente responde 401 | 0.000 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala viva cuyo expira_en ya pasó responde 401 aunque el job no la haya marcado | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin guardar nada | 0.083 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | El descubrimiento se guarda en la sala del token con la clave recibida | 0.002 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Repetir una clave devuelve el descubrimiento original sin guardar otro, sin evento y renovando la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un descubrimiento nuevo se guarda con el origen del rol, guarda su evento y luego recalcula los puzzles | 0.003 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Sin datos, el descubrimiento se guarda con un objeto vacío y origen juego si lo manda el juego | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Las cuatro claves del servidor dan 403 con cualquier rol y no tocan la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un resultado del servidor se guarda con origen servidor y sus datos, guarda su evento y luego recalcula los puzzles | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | radio se habilita con pescador_dialogo_completo y no antes | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | photo se habilita solo con las tres piezas, lleguen en el orden que lleguen | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Solo un puzzle bloqueado pasa a disponible: los que están disponibles, en curso o resueltos no cambian | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular pasa a disponible los puzzles habilitados y guarda un evento puzzle.habilitado de origen servidor con {puzzle, estado, datos} por cada uno | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | observatory pide coordenada_a, coordenada_b, observatory_enabled y lookout resuelto; falta uno y no se habilita | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular sin requisitos nuevos no cambia nada ni guarda eventos | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | lookout pide photo resuelto y lookout_enabled; la bandera puede llegar antes o después | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | en_curso más iniciado y resuelto más cualquier aviso responden el estado actual, solo renuevan la sala y no guardan eventos ni descubrimientos | 0.031 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | El evento lleva el origen del rol que avisó: juego o app | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin leer ni tocar el puzzle | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Las coordenadas salen de la configuración, sin convertirse a número | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible más iniciado pasa a en_curso, guarda los datos y puzzle.iniciado con {puzzle, estado, datos} y el origen del rol, sin crear descubrimiento | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un puzzle bloqueado da 409 con codigo puzzle_bloqueado ante cualquier aviso válido y no cambia nada | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Cada puzzle crea su resultado con origen servidor: coordenada_a con AR "137", coordenada_b con DEC "+042" como texto, observatorio_visible y magnetar_localizado | 0.004 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible o en_curso más completada pasa a resuelto: guarda puzzle.resuelto y después crea el resultado | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Si falta la fila del puzzle, un estado imposible, falla con un mensaje que nombra la sala y el puzzle, sin escribir nada | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | radio solo acepta {sintonizada: true}: iniciado o completada dan 400; los puzzles de la app no aceptan sintonizada | 0.003 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Solo vale un objeto con una clave aceptada y el booleano true: un valor que no es objeto, texto, número, false, null, claves extra, vacío o una clave ajena dan 400 con details en datos | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un id de puzzle desconocido da 400 con la lista de ids, también los viejos fotografia, mirador y observatorio y las variantes de mayúsculas o espacios | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearFueraDelCatalogoDa400SinTocarSalas | 0.075 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cambiar la escena la guarda y registra escena.cambiada con la anterior y la nueva | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | otraViolacionDeIntegridadNoSeDisfrazaDe409 | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseSinSalaVivaDa404 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | listarPueblosMarcaOcupadosLosQueTienenSalaViva | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseEnlazaConUnSoloUpdateYRenuevaLaExpiracion | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearConPuebloOcupadoDa409 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Unirse guarda el evento app.unida con origen app sobre la sala enlazada | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cerrar la sala la pasa a finalizada y guarda sala.finalizada con origen juego | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Sin salas vencidas, el job no registra nada | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Una escena fuera de introduccion, pueblo, faro, observatorio y desenlace da 400 con la lista de válidas | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Crear la sala guarda el evento sala.creada con origen juego y el código en el payload | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Unirse a una sala con expira_en vencido que el job aún no marcó responde 404 con el mensaje de sala inexistente | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | El estado ordena los puzzles y reporta los dispositivos que da la presencia del WebSocket | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Si unirse pierde la carrera no guarda ningún evento | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Repetir la escena actual responde igual y solo renueva la sala, sin evento | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseASalaVivaQueYaTieneAppDa409 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | La app no puede cerrar la sala: 403 sin tocarla | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | cadaSalaRecibeUnTokenDistinto | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearInsertaLosCuatroPuzzlesBloqueados | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | El job bloquea las salas vivas vencidas según el reloj, las marca expirada y guarda sala.expirada de cada una | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearNormalizaElCodigoYGuardaSoloElHashDelToken | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarDistingueElRolPorElHash | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarConTokenDesconocidoNoDaCredencial | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | La app no puede cambiar la escena: 403 sin tocar la sala | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Autenticar con el token de una sala viva con expira_en vencido no da credencial; justo en expira_en sí | 0.000 | ✅ pasó |
| unitaria | `ws.EmisorEventosTest` | Cada tipo de evento tiene su emisión: puzzle.actualizado y descubrimiento.nuevo a todos, escena.cambiada solo a la app, sala.cerrada cierra la sala, crear y unirse no emiten | 0.030 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al autenticarse, el cierre programado por el plazo de auth se cancela | 0.063 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Un auth rechazado no cancela el plazo: la sesión se cierra con 4001 y el plazo ya no hace nada | 0.001 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al abrir la conexión fija BLOCKING_SEND_TIMEOUT de Tomcat en la sesión con vestigia.ws.envio-limite, en milisegundos | 0.022 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al cerrarse una conexión sin autenticar, el cierre programado por el plazo de auth se cancela | 0.000 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | desconectar olvida una sesión rechazada, así que no queda guardada para siempre | 0.020 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Una sala que se cerró después de validar el token no registra la conexión: conectar devuelve false y no envía nada | 0.002 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | auth.ok es el primer mensaje de una conexión y los demás salen en el orden en que se encolaron | 0.003 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | La conexión reemplazada sigue autenticada hasta que Tomcat la cierra: un auth o un mensaje suyo no crean otra conexión ni otro close, y al desconectarse no avisa | 0.002 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Si la cola de una conexión pasa del límite de bytes, se descarta y la conexión se cierra con 4500 | 0.004 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Al desconectarse, lo que quedaba en la cola se descarta: no hay otro envío ni otro close sobre la sesión cerrada | 0.202 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Tras cerrarSala, la sesión sigue autenticada hasta que Tomcat la cierra: responder, conectar o cerrarSiNoSeAutentico no producen otro close ni otro envío | 0.003 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Si un envío falla, la conexión se cierra con 4500 y lo que seguía en su cola se descarta | 0.001 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Una sesión que el servidor ya decidió cerrar sin auth no se registra aunque su auth llegue después | 0.001 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Un envío que se bloquea no frena a nadie: enviar, dispositivos y la otra sala responden, y al soltarse sale todo en orden | 0.020 | ✅ pasó |
| integración | `AplicacionIT` | El programador que comparten el plazo de auth, los pings y el job de expiración tiene dos hilos | 0.001 | ✅ pasó |
| integración | `AplicacionIT` | rutaInexistenteBajoSalasDa404SinPedirToken | 0.004 | ✅ pasó |
| integración | `AplicacionIT` | elJobDeExpiracionEstaProgramadoCadaMinuto | 0.004 | ✅ pasó |
| integración | `AplicacionIT` | openApiDocumentaLosEndpointsDeSalasYElToken | 0.198 | ✅ pasó |
| integración | `AplicacionIT` | swaggerUiYOpenApiEstanDisponibles | 0.010 | ✅ pasó |
| integración | `AplicacionIT` | GET /pueblos con Accept: application/xml responde 406 con el error uniforme en JSON, no con un cuerpo vacío | 0.021 | ✅ pasó |
| integración | `AplicacionIT` | healthResponde200ConEstadoUp | 0.013 | ✅ pasó |
| integración | `CatalogoPueblosIT` | flywayAplicaV2YLosCodigosEstanEnLaTabla | 0.004 | ✅ pasó |
| integración | `CatalogoPueblosIT` | seedDeclara101CodigosValidosYSinDuplicados | 0.001 | ✅ pasó |
| integración | `DocumentacionIT` | La página de prueba /prueba-ws.html se sirve como HTML, manda el token en auth y no lo pone en la URL ni en el navegador | 0.004 | ✅ pasó |
| integración | `DocumentacionIT` | Cada operación tiene resumen, descripción y ejemplos; cada error usa ErrorBody con codigo en su ejemplo | 0.008 | ✅ pasó |
| integración | `DocumentacionIT` | Swagger UI se sirve en /swagger-ui/index.html | 0.002 | ✅ pasó |
| integración | `DocumentacionIT` | Las rutas con token declaran el esquema Bearer y las públicas no; el esquema es HTTP bearer | 0.005 | ✅ pasó |
| integración | `DocumentacionIT` | /v3/api-docs documenta exactamente las rutas y métodos reales de los controladores de la API | 0.005 | ✅ pasó |
| integración | `EscriturasSalaIT` | Con expira_en vencido y sin que corra el job, unirse da 404 y GET estado da 401 a los dos tokens; la sala no revive | 0.040 | ✅ pasó |
| integración | `EscriturasSalaIT` | pescador_dialogo_completo habilita radio y GET estado lo muestra disponible | 0.022 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala en esperando_app también se puede cerrar | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | El token del juego de otra sala recibe 403 en PUT escena y DELETE, y la sala no cambia | 0.019 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout_enabled que llega después de photo resuelto habilita lookout en la misma petición | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | Diez peticiones simultáneas con la misma clave dan un 201 y nueve 200, una sola fila y un solo evento | 0.028 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una escena inválida, vacía, de otro tipo o ausente da 400 y no cambia nada | 0.031 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cierra la sala: 204, queda finalizada sin renovar expira_en, guarda sala.finalizada y los dos tokens dan 401 | 0.031 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento, nuevo o repetido, renueva ultima_actividad y expira_en con la duración configurada | 0.018 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala con expira_en vencido que el job aún no marcó responde 401 a toda escritura y no revive | 0.024 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cerrar la sala libera el código: se puede crear otra sala con el mismo pueblo y la cerrada queda como historial | 0.020 | ✅ pasó |
| integración | `EscriturasSalaIT` | Crear y unirse guardan los eventos sala.creada (origen juego) y app.unida (origen app) | 0.011 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cada escritura deja su evento en orden: sala.creada, app.unida, descubrimiento.registrado, puzzle.habilitado, escena.cambiada y sala.finalizada | 0.024 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir la escena actual responde 200, renueva expira_en y no guarda otro escena.cambiada | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir una clave con otros datos responde 200 con los datos originales y no guarda otro evento | 0.018 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cerrar la sala y la sala sigue activa | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un JSON con una clave repetida da 400 JSON malformado en crear sala, descubrimientos, escena y puzzles, sin escribir nada; los mismos cuerpos sin repetir siguen funcionando | 0.044 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout y observatory se habilitan por servicio cuando se resuelve el puzzle previo, aunque la bandera haya llegado antes | 0.027 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento nuevo responde 201, aparece en GET estado y guarda descubrimiento.registrado con el origen del rol | 0.020 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez | 0.028 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las cuatro claves del servidor dan 403 al juego y a la app y no se guardan | 0.028 | ✅ pasó |
| integración | `EscriturasSalaIT` | El job guarda sala.expirada con origen servidor y deja ultima_actividad en la última escritura | 0.018 | ✅ pasó |
| integración | `EscriturasSalaIT` | Unos datos que pasan de 4096 bytes en JSON dan 400 y no se guardan; justo en el límite responden 201 | 0.019 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cambiar la escena, con cuerpo válido, inválido o vacío, y la escena no cambia | 0.016 | ✅ pasó |
| integración | `EscriturasSalaIT` | DELETE simultáneo con nueve descubrimientos: los que pierden reciben 401 y no dejan filas ni eventos después del cierre | 0.051 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un número o booleano JSON donde va texto da 400 sin convertirse: clave, escena y código de pueblo | 0.032 | ✅ pasó |
| integración | `EsquemaIT` | salasTerminadasNoBloqueanUnaNuevaSalaViva | 0.005 | ✅ pasó |
| integración | `EsquemaIT` | indiceEventosPorSalaYTiempoExiste | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | noPuedeHaberDosSalasVivasConElMismoCodigo | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | catalogoRechazaCodigosConMinusculasAcentosOEnie | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaCodigoFueraDelCatalogo | 0.001 | ✅ pasó |
| integración | `EsquemaIT` | flywayAplicaV1YCreaLasCincoTablas | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | alFinalizarUnaSalaElCodigoSePuedeReclamarDeNuevo | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | carreraPorElMismoPuebloDejaUnaSolaSalaViva | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaEstadoInvalido | 0.001 | ✅ pasó |
| integración | `JsonDocumentadoIT` | Cada ejemplo de respuesta de Swagger (/v3/api-docs) tiene los campos y tipos de una respuesta real | 0.085 | ✅ pasó |
| integración | `JsonDocumentadoIT` | Cada bloque JSON de respuesta de la documentación tiene los campos y tipos de una respuesta real | 0.011 | ✅ pasó |
| integración | `JsonDocumentadoIT` | La comparación detecta campos de más o de menos, tipos distintos, null fuera de codigo, coordenadas numéricas, avisos que no son booleanos y valores fuera de los enums | 0.004 | ✅ pasó |
| integración | `JsonDocumentadoIT` | Cada bloque JSON de api.md, tiempo-real.md y las tres guías está marcado como respuesta o como petición | 0.002 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos | 0.299 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | V3 renombra a inglés los puzzles de una sala creada con V2, conserva su estado y sus datos, y ese esquema rechaza fotografia | 0.169 | ✅ pasó |
| integración | `PuzzlesIT` | Con expira_en vencido y sin que corra el job, PUT puzzles responde 401 y no resuelve nada | 0.239 | ✅ pasó |
| integración | `PuzzlesIT` | Cada cambio de estado de un puzzle guarda {puzzle, estado, datos} y cada resultado guarda {clave, datos} con origen servidor | 0.131 | ✅ pasó |
| integración | `PuzzlesIT` | Los errores reales de PUT puzzles traen codigo null en 400, 401, 403, 404 y 405, y puzzle_bloqueado en el 409 | 0.064 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver es una sola transacción: si crear el resultado falla, el puzzle no queda resuelto, no se guarda ningún evento y la sala no se renueva | 0.102 | ✅ pasó |
| integración | `PuzzlesIT` | Un cliente no puede fabricar un resultado del servidor: POST de las cuatro claves da 403 y PUT de un puzzle bloqueado da 409, sin crear nada; el resultado real tiene origen servidor y los datos de la configuración | 0.094 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan antes que sus requisitos habilitan su puzzle en el PUT que completa el último requisito | 0.130 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan después de sus requisitos habilitan su puzzle en ese mismo POST | 0.113 | ✅ pasó |
| integración | `PuzzlesIT` | "+042" llega intacto como texto en GET estado y en la base (jsonb string), igual que "137" | 0.092 | ✅ pasó |
| integración | `PuzzlesIT` | Matriz de estados: bloqueado da 409; disponible+iniciado pasa a en_curso; en_curso+iniciado no cambia; en_curso+completada resuelve; resuelto+cualquier aviso no cambia ni duplica | 0.124 | ✅ pasó |
| integración | `PuzzlesIT` | observatory espera coordenada_a: con lookout resuelto y su bandera sigue bloqueado hasta que se resuelve radio, aunque llegue al final | 0.093 | ✅ pasó |
| integración | `PuzzlesIT` | Solo el booleano true: texto "true", 1, false, null, claves extra, objeto vacío, datos que no es objeto o datos ausente dan 400 con details en datos y no cambian nada; iniciado en radio también | 0.102 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver un puzzle actualiza ultima_actividad y renueva expira_en; repetir el aviso también renueva, sin guardar eventos | 0.041 | ✅ pasó |
| integración | `PuzzlesIT` | Un id de puzzle desconocido da 400 con la lista de ids, igual para los viejos fotografia, mirador y observatorio, y no toca la sala | 0.030 | ✅ pasó |
| integración | `PuzzlesIT` | Diez completada simultáneos sobre photo responden 200 y dejan una sola coordenada_b, un solo puzzle.resuelto y lookout habilitado una vez | 0.080 | ✅ pasó |
| integración | `PuzzlesIT` | La cadena completa por HTTP: pescador → radio → coordenada_a; tres piezas → photo → coordenada_b; lookout_enabled → lookout; observatory_enabled → observatory → magnetar_localizado | 0.110 | ✅ pasó |
| integración | `SalaIT` | crearDevuelveElTokenUnaVezYGuardaSoloSuHash | 0.024 | ✅ pasó |
| integración | `SalaIT` | carreraDeAppsPorUnirseDaUn200YSiete409 | 0.029 | ✅ pasó |
| integración | `SalaIT` | unirseSinSalaVivaDa404 | 0.008 | ✅ pasó |
| integración | `SalaIT` | flujoCrearUnirseEstado | 0.030 | ✅ pasó |
| integración | `SalaIT` | elJobNoTocaSalasFinalizadas | 0.030 | ✅ pasó |
| integración | `SalaIT` | unirseAceptaMinusculas | 0.014 | ✅ pasó |
| integración | `SalaIT` | pueblosReflejaLasSalasVivas | 0.017 | ✅ pasó |
| integración | `SalaIT` | estadoExigeElTokenDeEsaSala | 0.023 | ✅ pasó |
| integración | `SalaIT` | salaVencidaExpiraLiberaElNombreYSeConservaComoHistorial | 0.029 | ✅ pasó |
| integración | `SalaIT` | unirseRenuevaLaExpiracion | 0.015 | ✅ pasó |
| integración | `SalaIT` | carreraPorElMismoPuebloDaUn201YSiete409 | 0.012 | ✅ pasó |
| integración | `SalaIT` | crearFueraDelCatalogoDa400 | 0.004 | ✅ pasó |
| integración | `SalaIT` | crearAceptaMinusculasYEspacios | 0.009 | ✅ pasó |
| integración | `SalaIT` | crearInsertaLosCuatroPuzzlesBloqueados | 0.007 | ✅ pasó |
| integración | `SalaIT` | unirseDosVecesDa409 | 0.013 | ✅ pasó |
| integración | `SalaIT` | salaQueAunNoVenceSigueViva | 0.008 | ✅ pasó |
| integración | `repository.ModeloIT` | guardaYLeeSalaPuzzlesYDescubrimientosConJsonb | 0.035 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | Una conexión cuyo envío se queda bloqueado no frena GET estado, ni las escrituras de su sala, ni al otro dispositivo, ni a otra sala | 0.248 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | auth contra la expiración: si el job expira la sala entre validar el token y registrar la conexión, no queda conexión viva | 0.062 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | auth contra DELETE: si la sala se cierra entre validar el token y registrar la conexión, no queda conexión viva | 0.040 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | El job no expira una sala que una escritura simultánea renueva: espera su candado y vuelve a evaluar expira_en | 0.031 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Al cerrar la sala, los dos reciben sala.cerrada con estado finalizada y luego el servidor cierra con 1000 | 0.033 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | escena.cambiada llega solo a la app, con la escena nueva y la anterior; el juego no la recibe | 0.236 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Al expirar la sala, el job hace que los dos reciban sala.cerrada con estado expirada y cierra con 1000 | 0.047 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Los mensajes salen después del commit: nada mientras la transacción sigue abierta, y un GET estado pedido al recibir el mensaje ya ve el cambio aunque el commit tarde; un rollback no emite nada | 2.066 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Cada escritura REST de la cadena completa emite a los dos dispositivos su descubrimiento.nuevo y un puzzle.actualizado por cada cambio de estado, incluido bloqueado → disponible; otra sala no recibe nada | 0.321 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Las escrituras repetidas (descubrimiento, aviso de puzzle, misma escena) responden con éxito y no emiten nada | 0.068 | ✅ pasó |
| integración | `ws.WebSocketIT` | Antes de auth, otro tipo, JSON inválido o un mensaje binario cierran con 4000 | 0.009 | ✅ pasó |
| integración | `ws.WebSocketIT` | Ni los tokens ni Authorization aparecen en los logs durante auth, auth rechazada, errores y desconexión | 0.023 | ✅ pasó |
| integración | `ws.WebSocketIT` | Un token desconocido, vacío, que no es texto o ausente cierra con 4001 sin auth.ok | 0.009 | ✅ pasó |
| integración | `ws.WebSocketIT` | Origin: un navegador de un origen de CORS_ALLOWED_ORIGINS se conecta; otro origen recibe 403 en el handshake; sin Origin se conecta | 0.017 | ✅ pasó |
| integración | `ws.WebSocketIT` | Una segunda conexión del mismo rol reemplaza a la primera: la vieja cierra con 4009 y el otro rol no recibe desconectado | 0.348 | ✅ pasó |
| integración | `ws.WebSocketIT` | Presencia: cada conexión avisa dispositivo.conectado al otro rol, cada cierre dispositivo.desconectado, y GET estado lo refleja | 0.269 | ✅ pasó |
| integración | `ws.WebSocketIT` | auth con el token del juego o de la app responde auth.ok con su rol y la sala | 0.037 | ✅ pasó |
| integración | `ws.WebSocketIT` | El token de una sala cerrada o con expira_en vencido cierra con 4001, aunque el job no la haya marcado | 0.030 | ✅ pasó |
| integración | `ws.WebSocketIT` | El servidor manda pings a las conexiones autenticadas, programados cada 25 s | 0.032 | ✅ pasó |
| integración | `ws.WebSocketIT` | Ya autenticado, JSON inválido, binario, tipo desconocido, orientacion o un segundo auth responden error y la conexión sigue | 0.022 | ✅ pasó |
| integración | `ws.WebSocketIT` | Sin auth en 5 s el servidor cierra con 4008; una conexión que se autenticó a tiempo sigue abierta | 5.542 | ✅ pasó |
| integración | `ws.WebSocketIT` | Reconexión: la app vuelve con el mismo token, recibe auth.ok y no el mensaje que se perdió, y GET estado trae lo que pasó mientras no estaba | 0.256 | ✅ pasó |
| integración | `ws.WebSocketIT` | Un mensaje de 4096 caracteres recibe su error; uno de 4097 cierra con 1009 y avisa desconectado al otro rol | 0.035 | ✅ pasó |

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
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.307 s -- in com.vestigia.MigracionIdsPuzzlesIT
[INFO] Running com.vestigia.PuzzlesIT
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.561 s -- in com.vestigia.PuzzlesIT
[INFO] Running com.vestigia.AplicacionIT
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.261 s -- in com.vestigia.AplicacionIT
[INFO] Running com.vestigia.repository.ModeloIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.043 s -- in com.vestigia.repository.ModeloIT
[INFO] Running com.vestigia.EsquemaIT
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.032 s -- in com.vestigia.EsquemaIT
[INFO] Running com.vestigia.CatalogoPueblosIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.012 s -- in com.vestigia.CatalogoPueblosIT
[INFO] Running com.vestigia.DocumentacionIT
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.031 s -- in com.vestigia.DocumentacionIT
[INFO] Running com.vestigia.SalaIT
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.291 s -- in com.vestigia.SalaIT
[INFO] Running com.vestigia.JsonDocumentadoIT
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.109 s -- in com.vestigia.JsonDocumentadoIT
[INFO] Running com.vestigia.EscriturasSalaIT
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.666 s -- in com.vestigia.EscriturasSalaIT
[INFO] Running com.vestigia.ws.WebSocketIT
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.650 s -- in com.vestigia.ws.WebSocketIT
[INFO] Running com.vestigia.ws.EmisionWebSocketIT
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.779 s -- in com.vestigia.ws.EmisionWebSocketIT
[INFO] Running com.vestigia.ws.ConcurrenciaWebSocketIT
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.391 s -- in com.vestigia.ws.ConcurrenciaWebSocketIT
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 111, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] 
[INFO] --- failsafe:3.5.6:verify (default) @ vestigia ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  27.452 s
[INFO] Finished at: 2026-11-06T10:42:32-06:00
[INFO] ------------------------------------------------------------------------
```

## 6. Desarrolladores simulados

En este paso la sección 6 registra a los desarrolladores simulados que lanzó el orquestador, en lugar de pruebas adversariales de la API: el paso no cambió ningún endpoint. Cada uno recibió solo su guía y un backend real en `http://localhost:18080`, sin acceso al código del backend, y ejecutó cada paso con `curl`. En la ronda 1, los tres respondieron "La guía se pudo seguir completa: sí".

| Guía | Desarrollador simulado | Hallazgo | Corregido |
|---|---|---|:-:|
| Unity | gemini-3.8-flash (Antigravity) | U1: `new Uri("ws" + baseUrl.Substring(4) + "/ws")` se rompe con `/` final o sin esquema | ✅ `90791f8` |
| Unity | | U2: unirse con `curl` no dispara `dispositivo.conectado`; hace falta autenticar el WebSocket de la app | ✅ `90791f8` |
| Unity | | U3: el ejemplo de la sección 10 manda `pieza_1` antes de la escena `faro` | ✅ `90791f8` |
| Unity | | U4: no decía cómo simular `en_curso` | ✅ `90791f8` |
| Flutter | gemini-3.8-flash (Antigravity; sustituye a gemini-4-argon, sin cuota) | F1: recalcar al inicio que `BASE` se exporta con el puerto real (aplicado a las tres guías) | ✅ `90791f8`, `defac06`, `dca12f4` |
| Flutter | | F2 a F4: faltaban las respuestas de crear sala, escena y `DELETE` al simular al juego | ✅ `defac06` (marcadas para `JsonDocumentadoIT`) |
| Flutter | | F5: el estado recortado no decía qué descubrimientos omitía | ✅ `defac06` |
| Flutter | | F6: `_alCerrar` no terminaba la sesión con `1000` | ✅ `defac06` |
| PWA | claude-sonnet-5-5 | W1: `?disponibles=1`, `TRUE` y vacío responden `200`, no `400` | ✅ `dca12f4`. Verificado contra el backend: `1`, `yes` y `on` filtran; `0`, `no`, `off` y vacío devuelven todos; `si` da `400` |
| PWA | | W2: la regla de la `/` final es para `CORS_ALLOWED_ORIGINS`, no para lo que manda el navegador | ✅ `dca12f4` |
| PWA | | W3: decir que crear y unirse no piden token por diseño | ✅ `dca12f4`. La observación de seguridad queda para Raúl como P9 en `docs/PLAN.md` (`30c4617`) |
| PWA | | W4: `4001` y `4008` son cierres de WebSocket, no HTTP | ✅ `dca12f4` |
| PWA | | W5: precisar dónde y con qué versión se comprobó `tsc` | ✅ `dca12f4` |

Después de corregir, el constructor repitió los `curl` de Unity y Flutter al pie de la letra (sección 2).

**Ronda 2** (la última), con las guías corregidas y el backend real. Los tres siguieron su guía completa.

| Guía | Desarrollador simulado | Hallazgo | Corregido |
|---|---|---|:-:|
| Flutter | gemini-3.8-flash (Antigravity) | Sin hallazgos | — |
| Unity | gemini-3.8-flash (Antigravity) | U5: el bloque de WebSocket del paso 13 no traía el `descubrimiento.nuevo` de `observatory_enabled`, a diferencia de los pasos 7, 9 y 11 | ✅ `a4f1a64` (marcado para `JsonDocumentadoIT`) |
| Unity | | U6: en el ejemplo de la sección 10, dos `StartCoroutine` seguidos corren en paralelo | ✅ `a4f1a64`: encadenadas con `yield return`, y se aclara que el backend no exige ese orden |
| PWA | claude-sonnet-5-5 | W6: el cliente de la sección 10 solo corre dentro de Vite y React | ✅ `11d1c0d`, con la alternativa de `curl` o una versión sin React |
| PWA | | W7: un `Origin` configurado con `/` final no es fiable | ✅ `11d1c0d`. Comprobado contra el backend: hoy Spring quita la `/` del origen configurado y deja pasar `/api/**` y el handshake de `/ws`. La guía lo dice y pide configurarlo siempre sin `/`, porque esa tolerancia no es parte del contrato |
| PWA | | W8: el JSON de health está reformateado y su `Content-Type` real es otro | ✅ `11d1c0d`: `application/vnd.spring-boot.actuator.v3+json`, comprobado |
| PWA | | W9: `POST /unirse` sin sala da `404`; `GET /ws` sin saludo de WebSocket da `400` en texto plano | ✅ `11d1c0d`, con los textos reales |
| PWA | | W10: faltaba el `message` real del `406` | ✅ `11d1c0d`: `Acceptable representations: [application/json, application/*+json, application/yaml].` |

W7 a W10 se comprobaron con `curl` contra un backend propio en `http://localhost:18081` (contenedor `vestigia-pg-6c` en `55433`, borrado al terminar). Para W7 se arrancó con `https://con-barra.example/` en `CORS_ALLOWED_ORIGINS`.

## 7. Revisiones independientes

| Revisor | Modelo | Familia | Sustitución | Veredicto |
|---|---|---|---|---|
| A | gemini-3.8-flash (Antigravity) | Google: otra familia que la del constructor | Sustituye a gemini-4-argon, que no tenía cuota | Aprobado |
| B | claude-opus-5-5 | Anthropic: la misma familia que la del constructor | | Aprobado con 9 menores |

Todos los hallazgos se aceptaron:

| # | Revisor | Hallazgo | Decisión | Commit |
|---|---|---|---|---|
| A1 | A | La URL del WebSocket del cliente de Unity se rompe con `/` final o sin esquema (igual que U1) | Aceptado | `90791f8` |
| B1 | B | `JsonDocumentadoIT` no exigía booleanos en `completada`, `iniciado` y `sintonizada` ni validaba `estado`, `escena_actual`, `puzzle` y `origen` contra los enums | Aceptado. También valida `escena`, `anterior` y `rol` | `92071bb` |
| B2 | B | Un bloque ```` ```JSON ````, `jsonc` o `json title` se saltaba sin marca | Aceptado: también cuenta un bloque sin lenguaje que empieza con `{` o `[` | `92071bb` |
| B3 | B | La tolerancia `null`↔texto aplicaba a cualquier campo | Aceptado: solo en `codigo` | `92071bb` |
| B4 | B | El ejemplo `ESTADO` de Swagger mostraba `photo` `disponible` sin las tres piezas | Aceptado, con la parte opcional: una prueba nueva compara cada ejemplo de `/v3/api-docs` con una respuesta real | `92071bb` |
| B5 | B | La guía de Flutter decía que las minúsculas causan `404` | Aceptado | `defac06` |
| B6 | B | Detrás del proxy TLS de Railway, `/prueba-ws.html` podría recibir `403` en `/ws` y en `POST /salas` | Decisión de Raúl: anotado como O10 en `docs/PLAN.md`, con las dos salidas (`server.forward-headers-strategy=framework` o la URL pública en `CORS_ALLOWED_ORIGINS`). El Paso 7 lo retoma | `30c4617` |
| B7 | B | Tokens completos de 43 caracteres en `SalaCreada`, `AppUnida` y dos guías | Aceptado: recortados con `…` en las respuestas y `<token>` en el mensaje `auth` | `6142c15` |
| B8 | B | Crear sala y unirse en la página de prueba llamaban `json()` directo | Aceptado: leen texto y luego intentan JSON, como `rest()` | `3d373c9` |
| B9 | B | `/actuator/health` no aparece en `/v3/api-docs` | Aceptado como decisión documentada en `docs/api.md`, sin activar `springdoc.show-actuator` | `f7a9a7c` |

Sin cambio en el código: el `message` del `406` sale en inglés porque lo genera Spring. En la ronda 2 la guía de la PWA lo cita textual (W10).

**Re-revisión del arreglo de la ronda 1:** gemini-3.8-flash (Antigravity, Google, otra familia que la del constructor), en sustitución de gemini-4-argon, sin cuota. Veredicto: aprobado, sin problemas nuevos.

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
