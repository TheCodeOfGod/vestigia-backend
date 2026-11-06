# Plan de cambios del backend y la base de datos

Adapta el backend a los puzzles tal como funcionan en la app, **solo en lo que resultó viable**. Versión del 5 de noviembre de 2026, con las decisiones de Raúl ya incorporadas.

Los pendientes y las decisiones viven en `docs/PLAN.md`. Aquí está el **cómo**.

Leyenda: ✅ aprobado · 🟡 espera tu respuesta · ⏸️ diferido

---

## 1. Resumen

| Pregunta | Respuesta |
|---|---|
| ¿Cambia la base de datos? | Solo una migración (`V3`) que renombra tres ids. Ninguna tabla ni columna nueva |
| ¿Cambia el contrato? | Sí, en puzzles y coordenadas. Los endpoints del Paso 2 no cambian |
| ¿Se rehace el Paso 2? | No. Solo se renombran tres ids |
| ¿Qué tan grande es el Paso 3? | **Más chico que antes.** Los cuatro puzzles ahora se reducen a un aviso de "completado". El servidor ya no mide números |
| ¿Y el Paso 5? | **Se difiere.** El observatorio se juega entero en el teléfono, así que no hay que reenviar la orientación al juego |
| ¿Qué se pierde? | La radio con dial, las piezas con `slot` y `rot`, la validación de números del mirador y del observatorio, y los niveles de cercanía |

---

## 2. Decisiones que ya tomaste

| Tema | Decisión | |
|---|---|:-:|
| Coordenadas | AR `"137"` y DEC `"+042"`, como texto | ✅ |
| Radio | La resuelve el **juego** y avisa cuando se completó | ✅ |
| Mirador y observatorio | **La app indica cuándo se completó.** El servidor no valida los números | ✅ |
| Valores del mirador y el observatorio | Viven en la app | ✅ |
| Ids de puzzle | `radio`, `photo`, `lookout`, `observatory` (opción A, solo estos cuatro) | ✅ |
| Códigos `137` y `+042` en la app | La app los toma de lo que entrega el servidor | ✅ |
| Observatorio | Se juega **por completo en el teléfono**. El juego solo se entera de que terminó por la bandera `magnetar_localizado` | ✅ |
| Cercanía (frío, tibio, caliente) | No por ahora | ⏸️ |
| Telescopio de la PC moviéndose en tiempo real | No por ahora | ⏸️ |
| Flujo de la app | Inicio, escribir código de sala, libreta, burbuja que pulsa | ✅ |
| Habilitar mirador y observatorio | Con una bandera que manda el juego | 🟡 Nombres por confirmar |
| Validación en el servidor del mirador y el observatorio | Lo intentarás después si hay tiempo | ⏸️ |

---

## 3. Base de datos

| Cambio | ¿Hace falta? | Detalle |
|---|:-:|---|
| Renombrar ids de puzzle | ✅ Sí | Migración `V3__ids_puzzles_en_ingles.sql` |
| Tablas nuevas | No | `descubrimientos.datos` ya guarda `{tipo, valor}` |
| Columnas nuevas | No | |
| Índices nuevos | No | |

```sql
ALTER TABLE puzzles_estado DROP CONSTRAINT puzzles_estado_puzzle_check;

UPDATE puzzles_estado
   SET puzzle = CASE puzzle
                  WHEN 'fotografia'   THEN 'photo'
                  WHEN 'mirador'      THEN 'lookout'
                  WHEN 'observatorio' THEN 'observatory'
                  ELSE puzzle
                END;

ALTER TABLE puzzles_estado
  ADD CONSTRAINT puzzles_estado_puzzle_check
  CHECK (puzzle IN ('radio', 'photo', 'lookout', 'observatory'));
```

- **No se edita `V1`**: ya está aplicada. Se crea una migración nueva.
- El nombre `puzzles_estado_puzzle_check` es el que Postgres asigna por defecto. Lo compruebo contra un Postgres real con Testcontainers antes de dar la migración por buena.
- Es el momento barato: no hay nada desplegado en Railway, así que no hay filas reales que mover.

### Archivos que cambian por el renombrado

| Archivo | Cambio |
|---|---|
| `db/migration/V3__ids_puzzles_en_ingles.sql` | Nuevo |
| `model/PuzzleEstadoModel.java` | El enum pasa a `radio, photo, lookout, observatory` |
| `test/.../SalaIT.java` | Dos lugares que comparan nombres |
| `test/.../ModeloIT.java` | Usa `Puzzle.mirador` |
| `test/.../SalaServiceTest.java` | Usa `Puzzle.observatorio` |
| `docs/api.md`, `docs/modelo-datos.md`, `README.md` | Nombres en ejemplos y tablas |
| Guías de Unity y Flutter (PDF) | Se regeneran |

Las claves de descubrimiento (`pieza_1`, `coordenada_a`...), los campos y los estados **no se renombran**.

---

## 4. Cómo queda el Paso 3

Cada entrega es una rama `feature/...` desde `develop`, un PR a `develop`, con sus pruebas y su documentación en el mismo cambio.

### Entrega 3.0. Ids de puzzle
Rama `feature/ids-puzzles-ingles`. Migración `V3`, enum, pruebas y documentación (sección 3).

### Entrega 3a. Descubrimientos, escena y cierre

| Pieza | Qué hace |
|---|---|
| `POST /salas/{codigo}/descubrimientos` | Registra `{clave, datos}`. Idempotente: repetir no duplica. `403` si la clave es del servidor |
| `PUT /salas/{codigo}/escena` | Solo el juego. Cambia `escena_actual` |
| `DELETE /salas/{codigo}` | Solo el juego. Pasa la sala a `finalizada` |
| Cada escritura | Actualiza `ultima_actividad` y guarda un `evento` |
| Al registrar un descubrimiento | Recalcula qué puzzles pasan a `disponible` (sección 5) |
| Clases | `DescubrimientoService`, `DescubrimientoController`, métodos de escena y cierre en `SalaService`, DTOs de petición |

Claves que el servidor genera y los clientes **no** pueden mandar: `coordenada_a`, `coordenada_b`, `observatorio_visible`, `magnetar_localizado`.

### Entrega 3b. Los cuatro puzzles

Un solo endpoint: `PUT /salas/{codigo}/puzzles/{puzzle}` con `{datos: {...}}`.

| Puzzle | Quién lo manda | `datos` válidos | Resultado que crea el servidor |
|---|---|---|---|
| `radio` | Juego | `{sintonizada: true}` | `coordenada_a` = `{tipo: "ascension_recta", valor: "137"}` |
| `photo` | App | `{completada: true}` | `coordenada_b` = `{tipo: "declinacion", valor: "+042"}` |
| `lookout` | App | `{completada: true}` | `observatorio_visible` |
| `observatory` | App | `{completada: true}` | `magnetar_localizado` |

Reglas:

| Caso | Respuesta |
|---|---|
| `datos` no es exactamente el esperado, o el valor no es `true` | `400` |
| El puzzle está `bloqueado` | `409` (el código de error depende de P6) |
| El puzzle ya estaba `resuelto` | `200` con el estado actual, sin duplicar nada |
| Éxito | `200` con `{puzzle, estado, datos}`. Estado `resuelto`, descubrimiento creado con origen `servidor`, dependientes recalculados, `evento` guardado y `ultima_actividad` actualizada |

- Clases: `PuzzleService`, `PuzzleController`, DTOs `ReportarPuzzleRequest` y `PuzzleEstado`.
- Los puzzles no tienen lógica de números. Cada uno se valida con la misma regla de "una bandera `true`", así que no hacen falta validadores por tipo.
- 🟡 **P8:** si apruebas que la app avise `{iniciado: true}` al abrir el puzzle, el estado pasa a `en_curso` y las métricas pueden medir el tiempo de resolución. Se agrega como un valor más permitido en `photo`, `lookout` y `observatory`.

### Paso 5. Orientación ⏸️ Diferido

Ya no hace falta. El observatorio se juega entero en el teléfono y el juego se entera por el estado del puzzle y por el descubrimiento `magnetar_localizado`.

Si se reactiva (telescopio de la PC moviéndose en tiempo real), el diseño está en la sección 14 de `docs/PLAN.md`: mensajes `orientacion` solo con rol `app`, rangos de `azimut` (0 a 360) y `altitud` (−90 a 90), reenvío de la última lectura al juego, límite de ~40 por segundo y nada guardado.

---

## 5. Cómo se habilita un puzzle

El servidor recalcula tras cada descubrimiento o puzzle resuelto. **El orden en que lleguen las cosas no importa**: si el juego manda su bandera antes de que se cumplan los requisitos, el puzzle se habilita en cuanto se cumplan.

| Puzzle | Pasa a `disponible` cuando |
|---|---|
| `radio` | Existe `pescador_dialogo_completo` |
| `photo` | Existen `pieza_1`, `pieza_2` y `pieza_3` |
| `lookout` | `photo` está `resuelto` **y** existe `lookout_enabled` |
| `observatory` | Existen `coordenada_a` y `coordenada_b`, `lookout` está `resuelto` **y** existe `observatory_enabled` |

### La bandera del juego (tu último punto)

| Bandera | La manda el juego cuando | Cierra qué hueco |
|---|---|---|
| `lookout_enabled` | El jugador llega a la cima del faro | Evita que el mirador se habilite en cuanto se resuelva la foto, con el jugador todavía abajo |
| `observatory_enabled` | El jugador llega al observatorio y activa el telescopio | Lo mismo para el observatorio |

- Se registran con `POST /descubrimientos` y origen `juego`. **No hace falta ningún endpoint nuevo.**
- Los nombres siguen tu preferencia de dejar en inglés lo que no se muestra al usuario. Las claves que ya existen (`pescador_dialogo_completo`, `pieza_1`...) se quedan como están.
- 🟡 Hay que confirmar con quien hace Unity que puede mandarlas y en qué momento exacto.

---

## 6. Configuración nueva

Solo quedan los códigos del misterio, porque el servidor ya no mide nada más.

| Propiedad | Valor |
|---|---|
| `vestigia.puzzles.coordenadas.ar` | `137` |
| `vestigia.puzzles.coordenadas.dec` | `+042` |

Los valores del mirador y del observatorio (85 %, ±14, 6°, 2 s...) viven en la app y están listados en la sección 13 de `docs/PLAN.md`.

---

## 7. Pruebas por entrega

| Entrega | Unitarias (Mockito) | `@WebMvcTest` | Integración (Postgres real) |
|---|---|---|---|
| 3.0 Ids | Se ajustan las existentes | Sin cambios | `V3` migra filas viejas y el `CHECK` rechaza los ids anteriores |
| 3a | Idempotencia, claves del servidor, recálculo de disponibles | `403`, `401`, rol del juego en escena y cierre | Descubrimiento repetido sin duplicar. El cierre libera el código |
| 3b | Solo `true`, bloqueado, idempotencia, valores de las coordenadas, bandera antes o después de los requisitos | `400` y `409` con cuerpo uniforme | Cadena completa hasta `magnetar_localizado` |
| 5 ⏸️ | | | |

Se mantiene la regla de siempre: `./mvnw verify` antes de cada commit.

---

## 8. Documentación que se actualiza con cada entrega

| Documento | Cambio |
|---|---|
| `docs/api.md` | Los endpoints nuevos, con ejemplos de `curl` y errores |
| `docs/modelo-datos.md` | Ids de puzzle y claves de descubrimiento |
| `docs/PLAN.md` | Marcar entregas hechas |
| Guías de Unity y Flutter | Regenerar los PDF con los puzzles adaptados |

---

## 9. Orden recomendado

```
 3.0 Ids ──▶ 3a Descubrimientos ──▶ 3b Puzzles ──▶ Paso 4 ──▶ Paso 6      (Paso 5 diferido)
  libre         libre              espera P5, P6, P8
```

| Entrega | Puede empezar cuando |
|---|---|
| 3.0 | Das el visto bueno al Paso 3 |
| 3a | 3.0 termine |
| 3b | 3a termine, y estén resueltos P5, P6 y P8 |
| Paso 4 | 3b termine |
| Paso 6 | El Paso 4 termine |

---

## 10. Pendientes que siguen abiertos

| # | Pendiente | Bloquea | Mi recomendación |
|---|---|---|---|
| P5 | Confirmar los nombres `lookout_enabled` y `observatory_enabled` con Unity | 3b | Usar esos nombres |
| P6 | Dónde viaja el código `puzzle_bloqueado` | 3b | Agregar un campo `codigo` a todos los errores |
| P8 | ¿La app avisa `{iniciado: true}` al abrir el puzzle, para tener `en_curso` y los tiempos? | 3b | Sí |
| P7 | Archivo de salas terminadas para la PWA | Después del Paso 6 | |

---

## 11. Riesgos

| Riesgo | Efecto | Mitigación |
|---|---|---|
| El servidor solo confía en el aviso del cliente | Alguien podría marcar un puzzle como completado sin jugarlo | Aceptable en un juego de una sola jugadora. Queda la mejora futura de validar en el servidor |
| Sin avisos intermedios del mirador, el juego no recibe `lente_limpio` ni `obs_encontrado` | La PC no reacciona a cada paso del mirador | La app puede registrar esas banderas con `POST /descubrimientos` si Unity las necesita (C2) |
| Unity no confirma `137` y `+042` | El juego mostraría pistas distintas | Confirmar con Unity antes de la entrega 3b (C1) |
| El arte final del mirador cambia el objetivo | La app necesita nuevos valores | Están en la app, no en el servidor |
| Las guías PDF actuales están desactualizadas | El equipo de Unity y la app integra con datos viejos | Regenerar al terminar la entrega 3b |
