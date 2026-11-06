# Evidencia de pruebas por paso

Cada paso deja aquí su evidencia, dentro de su propio PR. Las cifras salen de los reportes de Maven con `python3 scripts/resumen-pruebas.py`, después de `./mvnw verify`.

| Paso | Rama | Fecha | Pruebas | Fallos | Veredicto | Detalle |
|---|---|---|---:|---:|:-:|---|
| 3.0 Ids de puzzle | `feature/ids-puzzles-ingles` | 2026-11-05 | 68 | 0 | ✅ Aprobado por dos revisores; 56 escenarios adversariales sin anomalías; 6 hallazgos menores corregidos | [paso-3.0-ids.md](paso-3.0-ids.md) |
| 3a Descubrimientos, escena y cierre | `feature/descubrimientos-escena-cierre` | 2026-11-05 | 123 | 0 | ✅ `./mvnw clean verify` en verde; adversariales y revisiones pendientes del orquestador | [paso-3a-descubrimientos.md](paso-3a-descubrimientos.md) |

La columna Fallos suma fallos y errores. Un paso cuenta como ✅ cuando `./mvnw verify` termina en `BUILD SUCCESS` con cero fallos y cero errores.
