# Insumos para el laboratorio de pruebas

Este material sirve para el laboratorio de pruebas de rendimiento y seguridad del backend de Vestigia. Describe qué hay hoy y qué se midió. No pide acceso a nada que no esté en el repo.

## Qué hay aquí

| Archivo | Para qué sirve |
|---|---|
| [`informe-metricas-backend.md`](informe-metricas-backend.md) | Plataforma, configuración, API, pruebas, CI y mediciones de 30 peticiones secuenciales contra producción (2026-11-09, commit `6a51818`). |
| [`evidencia-base-mejoras.md`](evidencia-base-mejoras.md) | Índices, configuración de Hikari, JPA y Actuator, límites de tasa y CI, según el commit `7f39eb1` (2026-11-16). |

Cada dato cita su fuente. Lo que dice "No disponible" no se pudo obtener, y el archivo explica por qué.

## Reglas para las pruebas

1. **No hagas pruebas de carga ni de seguridad contra producción.** Hay solo 101 pueblos y `POST /salas` no pide token ni tiene límite de tasa: una prueba de carga puede ocupar todos los pueblos y dejar sin sala a los jugadores reales.
2. **Levanta el backend en local** con [`README.md`](../../README.md#levantarlo-en-local) (Java 21, Docker y un Postgres de prueba). Todas las pruebas de carga, de fuerza bruta y de fuzzing van contra `http://localhost:8080`.
3. **Contra producción solo se permite lo de solo lectura y de bajo volumen**, como `GET /actuator/health` o `GET /api/v1/pueblos`, y con pocas peticiones. Antes, avisa a Raúl.
4. No pidas ni uses credenciales reales. Los tokens de las pruebas salen de crear salas en tu instancia local.
5. No cambies código del backend para medir. Si una medición necesita un cambio (por ejemplo, exponer `metrics` en Actuator), repórtalo a Raúl.
6. Los hallazgos van por escrito a Raúl. Nada de esto se publica ni se despliega sin su aprobación.

## Antes de usar los números

El informe de métricas es del 2026-11-09 y analiza el commit `6a51818`; la rama ha cambiado desde entonces. Repite las mediciones en local si las vas a citar.
