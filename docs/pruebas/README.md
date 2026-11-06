# Evidencia de pruebas por paso

Cada paso deja aquí su evidencia, dentro de su propio PR. Las cifras salen de los reportes de Maven con `python3 scripts/resumen-pruebas.py`, después de `./mvnw verify`.

| Paso | Rama | Fecha | Pruebas | Fallos | Veredicto | Detalle |
|---|---|---|---:|---:|:-:|---|
| 3.0 Ids de puzzle | `feature/ids-puzzles-ingles` | 2026-11-05 | 68 | 0 | ✅ | [paso-3.0-ids.md](paso-3.0-ids.md) |

La columna Fallos suma fallos y errores. Un paso cuenta como ✅ cuando `./mvnw verify` termina en `BUILD SUCCESS` con cero fallos y cero errores.
