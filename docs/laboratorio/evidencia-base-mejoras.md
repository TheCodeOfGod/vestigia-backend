# Evidencia base para mejoras del backend de Vestigia

- **Fecha:** 2026-11-16 (pruebas ejecutadas a las 20:51 CST)
- **Commit analizado:** `7f39eb1` (rama `feature/docs-sala-admin`). Solo lectura: no se modificó código.
- **Secretos:** no se incluye ningún valor sensible. Los valores por defecto de desarrollo de `DB_USER`/`DB_PASSWORD` no se reproducen.

---

## 1. Índices según las migraciones Flyway (V1 a V4)

Fuente: `src/main/resources/db/migration/V1__esquema.sql`, `V2__seed_pueblos.sql`, `V3__ids_puzzles_en_ingles.sql`, `V4__usuarios_y_sesiones_admin.sql` (`grep` de `INDEX`, `UNIQUE`, `PRIMARY KEY`). V2 solo inserta datos y V3 solo renombra ids y cambia un `CHECK`: ninguna de las dos crea ni borra índices.

| Tabla | Índices (incluye los implícitos de PK y UNIQUE) | Origen |
|---|---|---|
| `codigos_pueblo` | PK `(codigo)` | V1 |
| `salas` | PK `(id)`; **índice único parcial** `salas_codigo_viva ON salas(codigo) WHERE estado IN ('esperando_app','activa')` | V1 |
| `puzzles_estado` | PK compuesta `(sala_id, puzzle)` | V1 |
| `descubrimientos` | PK `(id)`; `UNIQUE (sala_id, clave)` | V1 |
| `eventos` | PK `(id)`; `eventos_sala_tiempo (sala_id, creado_en)` | V1 |
| `usuarios` | PK `(id)`; `usuarios_correo_unico UNIQUE (correo)` | V4 |
| `sesiones_admin` | PK `(token_hash)`; `sesiones_admin_usuario (usuario_id)`; `sesiones_admin_expira (expira_en)` | V4 |

Las claves foráneas (`salas.codigo`, `puzzles_estado.sala_id`, `descubrimientos.sala_id`, `eventos.sala_id`, `sesiones_admin.usuario_id`) no crean índice por sí solas en PostgreSQL; solo los de la tabla anterior existen.

**¿Hay índice sobre `token_juego_hash`, `token_app_hash` o `estado` en `salas`? No.**
- Ninguna migración menciona `token_juego_hash` o `token_app_hash` fuera de su definición de columna.
- El único índice de `salas` que toca `estado` es el parcial por `codigo`, cuyo predicado usa `estado` pero cuya clave es `codigo`. No sirve para filtrar por `estado` ni por hash.
- Consultas del código que filtran por esas columnas (`SalaRepository.java`): `findByTokenHashAndEstadoIn` (`token_juego_hash = :hash OR token_app_hash = :hash`, más `estado`), `findByEstado`, `bloquearVencidas` (`estado in … and expira_en < :ahora`) y `findCodigosByEstadoIn`.
- **No se ejecutó `EXPLAIN` contra la base**, así que no hay evidencia medida de que esas consultas hagan un recorrido secuencial. Lo único comprobado es la ausencia de índices en las migraciones. "Plan de ejecución: No disponible" (no se tiene acceso a la base de producción y no se montó una local con datos).
- Tampoco existe índice sobre `salas.creada_en` (columna por la que ordena `GET /admin/salas`) ni sobre `salas.expira_en`.

---

## 2. Configuración

Fuente: `src/main/resources/application.properties` y `Dockerfile`. No hay otros archivos de propiedades ni YAML en `src/main/resources` (`ls`). Los valores `${DB_URL:…}` toman la variable de entorno si existe.

### 2.1 Pool de conexiones (Hikari)

| Propiedad | Valor en el repo | Predeterminado de Spring Boot/HikariCP |
|---|---|---|
| `spring.datasource.hikari.*` (cualquiera) | **No configurada** (`grep -ri "hikari\|pool" src/main/resources` solo encuentra la línea del programador, ver abajo) | Se usa HikariCP con sus valores por defecto |
| `maximum-pool-size`, `minimum-idle`, `connection-timeout`, `max-lifetime`, `idle-timeout` | No configurados | Valores de HikariCP: tamaño máximo 10, espera de conexión 30 s, vida máxima 30 min, inactividad 10 min. **Son valores de la biblioteca, no verificados en ejecución** (Actuator no expone métricas, ver sección 4) |
| `spring.datasource.url / username / password` | `${DB_URL:…}`, `${DB_USER:…}`, `${DB_PASSWORD:…}` (valores por defecto de desarrollo local no reproducidos) | Sin predeterminado de Spring para estos |
| `spring.task.scheduling.pool.size` | `2` | El predeterminado de Spring Boot es 1; aquí **no** es el predeterminado. No es un pool de BD: es el programador de tareas (plazo de auth del WS, pings y job de expiración) |

### 2.2 JPA

| Propiedad | Valor | ¿Predeterminado? |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `validate` | No (con una BD real el predeterminado de Spring Boot sin base embebida es `none`) |
| `spring.jpa.open-in-view` | `false` | No (el predeterminado es `true`) |
| `spring.jackson.property-naming-strategy` | `SNAKE_CASE` | No (aunque no es de JPA, afecta la serialización) |

### 2.3 Actuator

| Propiedad | Valor | ¿Predeterminado? |
|---|---|---|
| `management.endpoints.web.exposure.include` | `health` | Predeterminado de Spring Boot: `health` (solo ese). **Coincide con el predeterminado**, aunque está declarado explícitamente |

### 2.4 Expiración de salas

| Propiedad | Valor | ¿Predeterminado? |
|---|---|---|
| `vestigia.salas.expiracion` | `PT2H` | Propiedad propia del proyecto (no es de Spring Boot) |
| `vestigia.salas.expiracion-revision` | `PT1M` | Propiedad propia del proyecto |

Otras propiedades propias relevantes para rendimiento: `vestigia.ws.ping=PT25S`, `vestigia.ws.auth-plazo=PT5S`, `vestigia.ws.envio-max-bytes=65536`, `vestigia.auth.intentos-max=5`, `vestigia.auth.intentos-ventana=PT15M`.

### 2.5 ENTRYPOINT del Dockerfile (línea 16)

```
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- **No define opciones de JVM**: sin `-Xmx`, `-Xms`, `-XX:MaxRAMPercentage`, ni `JAVA_TOOL_OPTIONS`/`JAVA_OPTS` (`grep` en `Dockerfile` y `docs/despliegue.md`). La JVM usa los valores por defecto del JRE en contenedor (`eclipse-temurin:21-jre`).
- Si Railway define `JAVA_TOOL_OPTIONS` como variable del servicio, no se puede ver desde el repo: "No disponible" (no se tiene acceso al panel de Railway).

---

## 3. Rutas públicas y limitación de tasa

Fuente: `SeguridadConfig.java` (interceptores), controllers, `WebSocketConfig.java`, `docs/despliegue.md`, `AuthService.java`. No hay Spring Security: la autenticación es por dos interceptores que cubren `/api/v1/salas/**` (menos `POST /salas` y `…/unirse`) y `/api/v1/admin/**`, `/api/v1/auth/yo`, `/api/v1/auth/logout`. Todo lo demás es público.

| Ruta | Método | Observación |
|---|---|---|
| `/api/v1/pueblos` | GET | Catálogo de pueblos y cuáles están libres |
| `/api/v1/salas` | POST | Crea sala y entrega el token del juego, sin token (por diseño, P9) |
| `/api/v1/salas/{codigo}/unirse` | POST | Entrega el token de la app, sin token (por diseño) |
| `/api/v1/auth/login` | POST | Inicio de sesión de administradores |
| `/actuator/health` | GET | Salud |
| `/v3/api-docs`, `/swagger-ui/index.html` (y recursos de Swagger) | GET | Públicos por decisión registrada; se apagan con `SPRINGDOC_API_DOCS_ENABLED=false` y `SPRINGDOC_SWAGGER_UI_ENABLED=false` (`docs/despliegue.md`) |
| `/prueba-ws.html` | GET | Página estática; se apaga con `SPRING_WEB_RESOURCES_ADD_MAPPINGS=false` |
| `/ws` | WebSocket | El handshake es público; la autenticación es el primer mensaje `auth` en menos de 5 s |

**Limitación de tasa:**
- **Solo existe en `POST /auth/login`**: 5 intentos fallidos por correo en 15 min, después `429` con `codigo: demasiados_intentos` (`vestigia.auth.intentos-max` e `intentos-ventana`; `AuthService.java`). Está en memoria, por correo (no por IP; el código y `CLAUDE.md` indican que la IP no es fiable tras el proxy de Railway) y se pierde al reiniciar.
- **Ninguna otra ruta tiene limitación de tasa.** No se encontró código de rate limiting en `config/` ni dependencias para ello (`grep -rniE "rate|bucket|throttle"` solo coincide con los intentos de login y términos no relacionados).
- Límites que no son de tasa pero acotan el abuso: mensaje WebSocket ≤ 4096 caracteres, cola de salida ≤ 64 KB, plazo de 5 s para autenticar, `datos` de descubrimientos ≤ 4096 bytes.
- Un límite de tasa a nivel de Railway o de su proxy: "No disponible" (no visible desde el repo).

---

## 4. Actuator

Fuente: `application.properties` línea 15, `docs/despliegue.md`.

- **Expuesto por web:** únicamente `health` (`management.endpoints.web.exposure.include=health`). No se expone `metrics`, `info`, `env`, `beans` ni `prometheus`.
- **Quién puede acceder:** **cualquiera, sin autenticación**: `/actuator/health` no está cubierto por ninguno de los dos interceptores (sección 3) y no hay Spring Security. El cuerpo es `{"groups":["liveness","readiness"],"status":"UP"}` sin detalles de componentes (verificado en la medición del 2026-11-09, que también mostró `GET /actuator/metrics` → `404`). Esta verificación no se repitió hoy.
- **Consecuencia:** métricas de JVM, hilos, pool de conexiones y tiempos HTTP no están disponibles en producción.

---

## 5. Pruebas

Fuente: `./mvnw -B verify`, ejecutado el 2026-11-16 a las 20:51 CST en el equipo local con Docker (Testcontainers con PostgreSQL real). Salida completa guardada en la carpeta temporal de la sesión.

| Fase | Pruebas | Falladas | Errores | Omitidas |
|---|---:|---:|---:|---:|
| Unitarias (Surefire) | 170 | 0 | 0 | 0 |
| Integración (Failsafe) | 139 | 0 | 0 | 0 |
| **Total** | **309** | **0** | **0** | **0** |

- **Resultado:** `BUILD SUCCESS`.
- **Tiempo total de Maven:** **42.0 s** (`Total time: 42.049 s`).
- Antes (2026-11-09, commit `6a51818`) eran 307 pruebas en 37.2 s.
- **JaCoCo: no existe** en `pom.xml` (`grep -c -i jacoco pom.xml` → 0). Cobertura: **No disponible** porque no hay plugin de cobertura configurado.

---

## 6. CI

Fuente: `gh run list --limit 20 --json databaseId,workflowName,conclusion,createdAt,updatedAt` (2026-11-16). Duración = `updatedAt − createdAt`. Se pidió la lista tal cual; no se incluyó `startedAt`, por lo que la duración incluye cualquier espera previa al inicio del job.

| Run | Workflow | Resultado | Duración |
|---|---|---|---:|
| 43369902642 | Health de producción | success | 24 s |
| 43369897172 | Health de producción | success | 22 s |
| 43369753080 | Health de producción | skipped | 8 s |
| 43366764942 | Health de producción | success | 22 s |
| 43360638681 | Health de producción | skipped | 1 s |
| 43360631656 | CI | success | 172 s |
| 43359611639 | CI | success | 156 s |
| 43359215124 | Health de producción | success | 22 s |
| 43359084743 | CI | success | 150 s |
| 43358960406 | Health de producción | skipped | 1 s |
| 43353528026 | CI | success | 155 s |
| 42543903622 | CI | success | 175 s |
| 42543510802 | CI | success | 168 s |
| 42529185644 | Health de producción | success | 22 s |
| 42527848288 | Health de producción | skipped | 1 s |
| 42527842280 | CI | success | 157 s |
| 42527409273 | CI | success | 156 s |
| 42526637434 | CI | success | 135 s |
| 42525436498 | CI | success | 173 s |
| 42525228540 | Health de producción | success | 23 s |

| Métrica | Valor |
|---|---|
| Ejecuciones analizadas | 20 (10 de CI, 10 de Health de producción) |
| `success` / `skipped` / `failure` | 17 / 3 / 0 |
| **Tasa de éxito sin omitidas** | **17 de 17 = 100 %** |
| Tasa de éxito contando las omitidas como no exitosas | 17 de 20 = 85 % |
| **Duración promedio de CI** | **159.7 s (≈ 2 min 40 s)** sobre 10 corridas, todas `success`; rango 135 a 175 s |
| Duración promedio de Health de producción (solo `success`, 6 corridas) | 22.5 s |

Las 3 `skipped` son del workflow de salud, que solo corre si el despliegue termina en `success` (condición del `if` en `salud-produccion.yml`). Muestra pequeña: 10 corridas de CI.

---

## 7. Resumen de lo "No disponible"

| Dato | Motivo |
|---|---|
| Plan de ejecución (`EXPLAIN`) de las consultas sin índice | No hay acceso a la base de producción y no se montó una base local con datos |
| Valores efectivos del pool Hikari en ejecución | Actuator solo expone `health`; se citan solo los valores por defecto de la biblioteca |
| `JAVA_TOOL_OPTIONS` u opciones de JVM definidas en Railway | Solo se vio el repo, no el panel de Railway |
| Limitación de tasa en el proxy de Railway | No visible desde el repo |
| Cobertura de código | No hay JaCoCo en `pom.xml` |
