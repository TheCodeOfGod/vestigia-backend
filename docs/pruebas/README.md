# Evidencia de pruebas por paso

Cada paso deja aquí su evidencia, dentro de su propio PR. Las cifras salen de los reportes de Maven con `python3 scripts/resumen-pruebas.py`, después de `./mvnw verify`.

| Paso | Rama | Fecha | Pruebas | Fallos | Veredicto | Detalle |
|---|---|---|---:|---:|:-:|---|
| 3.0 Ids de puzzle | `feature/ids-puzzles-ingles` | 2026-11-05 | 68 | 0 | ✅ Aprobado por dos revisores; 56 escenarios adversariales sin anomalías; 6 hallazgos menores corregidos | [paso-3.0-ids.md](paso-3.0-ids.md) |
| 3a Descubrimientos, escena y cierre | `feature/descubrimientos-escena-cierre` | 2026-11-06 | 143 | 0 | ✅ Aprobado por dos revisores; 67 escenarios adversariales con 2 ❌ (coerción de tipos) corregidos; 7 hallazgos de revisión y 2 del constructor corregidos; B3 queda como decisión de Raúl | [paso-3a-descubrimientos.md](paso-3a-descubrimientos.md) |
| 3b Los cuatro puzzles | `feature/puzzles` | 2026-11-06 | 185 | 0 | ✅ Aprobado por dos revisores; dos probadores sin fallos de contrato, con 3 ❌ corregidos (`datos` no objeto, claves repetidas, `406` vacío); B1 a B4 corregidos; P2-2 y P2-3 documentados como limitaciones | [paso-3b-puzzles.md](paso-3b-puzzles.md) |
| 4 WebSocket | `feature/websocket` | 2026-11-06 | 226 | 0 | ✅ Ronda 1: revisor A (otra familia) rechazó con correcciones y B sin bloqueantes; un probador con 45 escenarios sin anomalías; H1 (candado global con red y base de datos dentro, bloqueante) a H6 corregidos. Ronda 2: los dos re-revisores aprobaron; R1 a R7 corregidos. Dos `clean verify` seguidos en verde | [paso-4-websocket.md](paso-4-websocket.md) |
| 6 Cierre y guías por cliente | `feature/cierre` | 2026-11-06 | 235 | 0 | ✅ Dos revisores aprobaron en la ronda 1 (A de otra familia; B con 9 menores) y la re-revisión (otra familia) aprobó sin problemas nuevos. Desarrolladores simulados: en la ronda 1 los tres siguieron su guía completa; en la ronda 2 Flutter sin hallazgos y Unity y PWA con 7 detalles de documentación, todos corregidos. B6 y W3 quedan como decisiones de Raúl en `docs/PLAN.md` | [paso-6-cierre.md](paso-6-cierre.md) |

La columna Fallos suma fallos y errores. Un paso cuenta como ✅ cuando `./mvnw verify` termina en `BUILD SUCCESS` con cero fallos y cero errores.
