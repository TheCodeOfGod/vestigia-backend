package com.vestigia.controller;

/**
 * Ejemplos de petición y respuesta que muestra Swagger UI. Salieron de correr el backend real; los tokens están
 * recortados. Los comparten los cuatro controladores, por eso viven en su propio archivo.
 */
final class EjemplosOpenApi {

	static final String PUEBLOS = """
			[{"codigo": "ACAXOCHITLAN", "libre": true}, {"codigo": "ACULCO", "libre": true}, \
			{"codigo": "AHUACATLAN", "libre": true}]""";

	static final String CREAR_SALA = """
			{"codigo": "bacalar"}""";

	static final String SALA_CREADA = """
			{"codigo": "BACALAR", "estado": "esperando_app", "token": "K8srOIYIL0jr…"}""";

	static final String APP_UNIDA = """
			{"token": "T6F973q3JsTu…"}""";

	static final String ESTADO = """
			{
			  "codigo": "BACALAR",
			  "estado": "activa",
			  "escena_actual": "faro",
			  "puzzles": [
			    {"puzzle": "radio", "estado": "resuelto", "datos": {"sintonizada": true}},
			    {"puzzle": "photo", "estado": "disponible", "datos": {}},
			    {"puzzle": "lookout", "estado": "bloqueado", "datos": {}},
			    {"puzzle": "observatory", "estado": "bloqueado", "datos": {}}
			  ],
			  "descubrimientos": [
			    {"clave": "pescador_dialogo_completo", "datos": {}, "origen": "juego", \
			"creado_en": "2026-11-06T15:45:32.305120Z"},
			    {"clave": "coordenada_a", "datos": {"tipo": "ascension_recta", "valor": "137"}, "origen": "servidor", \
			"creado_en": "2026-11-06T15:45:32.334242Z"},
			    {"clave": "pieza_1", "datos": {}, "origen": "juego", "creado_en": "2026-11-06T15:45:32.354251Z"},
			    {"clave": "pieza_2", "datos": {}, "origen": "juego", "creado_en": "2026-11-06T15:45:32.364256Z"},
			    {"clave": "pieza_3", "datos": {}, "origen": "juego", "creado_en": "2026-11-06T15:45:32.372746Z"}
			  ],
			  "dispositivos": {"juego": true, "app": true},
			  "creada_en": "2026-11-06T15:45:31.735796Z",
			  "expira_en": "2026-11-06T17:45:32.372746Z"
			}""";

	static final String REGISTRAR_DESCUBRIMIENTO = """
			{"clave": "pieza_1", "datos": {"lugar": "muelle"}}""";

	static final String BANDERA_JUEGO = """
			{"clave": "lookout_enabled"}""";

	static final String DESCUBRIMIENTO = """
			{"clave": "pieza_1", "datos": {"lugar": "muelle"}, "origen": "juego", \
			"creado_en": "2026-11-06T15:45:32.354251Z"}""";

	static final String AVISO_COMPLETADA = """
			{"datos": {"completada": true}}""";

	static final String AVISO_INICIADO = """
			{"datos": {"iniciado": true}}""";

	static final String AVISO_SINTONIZADA = """
			{"datos": {"sintonizada": true}}""";

	static final String PUZZLE = """
			{"puzzle": "photo", "estado": "resuelto", "datos": {"completada": true}}""";

	static final String CAMBIAR_ESCENA = """
			{"escena": "faro"}""";

	static final String ESCENA = """
			{"escena_actual": "faro"}""";

	static final String ERROR_400_VALIDACION = """
			{"timestamp": "2026-11-06T15:45:32.469369Z", "status": 400, "error": "Bad Request", "codigo": null, \
			"message": "Datos inválidos", "path": "/api/v1/salas/BACALAR/puzzles/photo", \
			"details": [{"field": "datos", "message": "en photo debe ser exactamente {\\"completada\\": true} o \
			{\\"iniciado\\": true}"}]}""";

	static final String ERROR_400_JSON = """
			{"timestamp": "2026-11-06T15:45:32.469369Z", "status": 400, "error": "Bad Request", "codigo": null, \
			"message": "JSON malformado o ilegible", "path": "/api/v1/salas/BACALAR/descubrimientos", "details": []}""";

	static final String ERROR_400_CATALOGO = """
			{"timestamp": "2026-11-06T15:45:31.819834Z", "status": 400, "error": "Bad Request", "codigo": null, \
			"message": "'ATLANTIDA' no está en el catálogo de pueblos", "path": "/api/v1/salas", "details": []}""";

	static final String ERROR_400_PUEBLOS = """
			{"timestamp": "2026-11-06T15:45:31.819834Z", "status": 400, "error": "Bad Request", "codigo": null, \
			"message": "Valor inválido para 'disponibles'", "path": "/api/v1/pueblos", "details": []}""";

	static final String ERROR_400_ESCENA = """
			{"timestamp": "2026-11-06T15:45:32.450749Z", "status": 400, "error": "Bad Request", "codigo": null, \
			"message": "'bosque' no es una escena válida. Usa una de: introduccion, pueblo, faro, observatorio, \
			desenlace", "path": "/api/v1/salas/BACALAR/escena", "details": []}""";

	static final String ERROR_400_PUZZLE = """
			{"timestamp": "2026-11-06T15:45:32.469369Z", "status": 400, "error": "Bad Request", "codigo": null, \
			"message": "'fotografia' no es un puzzle válido. Usa uno de: radio, photo, lookout, observatory", \
			"path": "/api/v1/salas/BACALAR/puzzles/fotografia", "details": []}""";

	static final String ERROR_401 = """
			{"timestamp": "2026-11-06T15:45:32.466723Z", "status": 401, "error": "Unauthorized", "codigo": null, \
			"message": "Token inválido o de una sala que ya terminó", "path": "/api/v1/salas/BACALAR/estado", \
			"details": []}""";

	static final String ERROR_403_OTRA_SALA = """
			{"timestamp": "2026-11-06T15:45:32.453624Z", "status": 403, "error": "Forbidden", "codigo": null, \
			"message": "El token no pertenece a esta sala", "path": "/api/v1/salas/TULUM/estado", "details": []}""";

	static final String ERROR_403_CLAVE_SERVIDOR = """
			{"timestamp": "2026-11-06T15:45:32.453624Z", "status": 403, "error": "Forbidden", "codigo": null, \
			"message": "La clave 'coordenada_a' la genera el servidor al resolver un puzzle; los clientes no pueden \
			enviarla", "path": "/api/v1/salas/BACALAR/descubrimientos", "details": []}""";

	static final String ERROR_403_CLAVE_SOLO_JUEGO = """
			{"timestamp": "2026-11-07T15:39:38.400910Z", "status": 403, "error": "Forbidden", \
			"codigo": "clave_solo_juego", "message": "La clave 'pieza_1' la registra el juego; la app no puede enviarla", \
			"path": "/api/v1/salas/BACALAR/descubrimientos", "details": []}""";

	static final String ERROR_403_PUZZLE_SOLO_APP = """
			{"timestamp": "2026-11-08T15:39:38.400910Z", "status": 403, "error": "Forbidden", \
			"codigo": "puzzle_solo_app", "message": "Los puzzles los avisa la app; el juego no puede reportar 'radio'", \
			"path": "/api/v1/salas/BACALAR/puzzles/radio", "details": []}""";

	static final String ERROR_403_ESCENA = """
			{"timestamp": "2026-11-06T15:45:32.450749Z", "status": 403, "error": "Forbidden", "codigo": null, \
			"message": "Solo el juego puede cambiar la escena", "path": "/api/v1/salas/BACALAR/escena", \
			"details": []}""";

	static final String ERROR_403_CIERRE = """
			{"timestamp": "2026-11-06T15:45:33.083156Z", "status": 403, "error": "Forbidden", "codigo": null, \
			"message": "Solo el juego puede cerrar la sala", "path": "/api/v1/salas/BACALAR", "details": []}""";

	static final String ERROR_404 = """
			{"timestamp": "2026-11-06T15:45:31.934995Z", "status": 404, "error": "Not Found", "codigo": null, \
			"message": "No hay una sala en curso con el código 'TULUM'", "path": "/api/v1/salas/TULUM/unirse", \
			"details": []}""";

	static final String ERROR_409_OCUPADO = """
			{"timestamp": "2026-11-06T15:45:31.819834Z", "status": 409, "error": "Conflict", "codigo": null, \
			"message": "'BACALAR' ya tiene una sala en curso", "path": "/api/v1/salas", "details": []}""";

	static final String ERROR_409_APP = """
			{"timestamp": "2026-11-06T15:45:31.930084Z", "status": 409, "error": "Conflict", "codigo": null, \
			"message": "La sala 'BACALAR' ya tiene una app enlazada", "path": "/api/v1/salas/BACALAR/unirse", \
			"details": []}""";

	static final String ERROR_409_BLOQUEADO = """
			{"timestamp": "2026-11-06T15:45:32.291122Z", "status": 409, "error": "Conflict", \
			"codigo": "puzzle_bloqueado", "message": "El puzzle 'radio' está bloqueado: todavía no se cumplen sus \
			requisitos", "path": "/api/v1/salas/BACALAR/puzzles/radio", "details": []}""";

	private EjemplosOpenApi() {
	}

	static final String LOGIN = """
			{"correo": "raul@vestigia.site", "password": "una-contraseña-larga"}""";

	static final String USUARIO = """
			{"id": "5b0c1f3e-7d0b-4f5e-9a39-2f0f5e8f6a11", "nombre": "Raúl", "correo": "raul@vestigia.site", \
			"rol": "admin", "activo": true, "creado_en": "2026-11-08T23:10:00.000000Z"}""";

	static final String SESION = "{\"token\": \"Zk3o1x0b8QmD…\", \"expira_en\": \"2026-11-09T07:10:00.000000Z\", "
			+ "\"usuario\": " + USUARIO + "}";

	static final String USUARIOS = "[" + USUARIO + "]";

	static final String CREAR_USUARIO = """
			{"nombre": "María", "correo": "maria@vestigia.site", "password": "otra-contraseña", "rol": "admin"}""";

	static final String ACTUALIZAR_USUARIO = """
			{"activo": false}""";

	static final String ERROR_400_USUARIO = """
			{"timestamp": "2026-11-08T23:10:00.000000Z", "status": 400, "error": "Bad Request", "codigo": null, \
			"message": "Datos inválidos", "path": "/api/v1/admin/usuarios", \
			"details": [{"field": "password", "message": "debe tener de 8 a 72 caracteres"}]}""";

	static final String ERROR_401_LOGIN = """
			{"timestamp": "2026-11-08T23:10:00.000000Z", "status": 401, "error": "Unauthorized", "codigo": null, \
			"message": "Correo o contraseña incorrectos", "path": "/api/v1/auth/login", "details": []}""";

	static final String ERROR_401_ADMIN = """
			{"timestamp": "2026-11-08T23:10:00.000000Z", "status": 401, "error": "Unauthorized", "codigo": null, \
			"message": "Sesión inválida o vencida: vuelve a iniciar sesión", "path": "/api/v1/admin/usuarios", \
			"details": []}""";

	static final String ERROR_403_ADMIN = """
			{"timestamp": "2026-11-08T23:10:00.000000Z", "status": 403, "error": "Forbidden", "codigo": null, \
			"message": "Se necesita el rol admin", "path": "/api/v1/admin/usuarios", "details": []}""";

	static final String ERROR_404_USUARIO = """
			{"timestamp": "2026-11-08T23:10:00.000000Z", "status": 404, "error": "Not Found", "codigo": null, \
			"message": "No existe el usuario 5b0c1f3e-7d0b-4f5e-9a39-2f0f5e8f6a11", \
			"path": "/api/v1/admin/usuarios/5b0c1f3e-7d0b-4f5e-9a39-2f0f5e8f6a11", "details": []}""";

	static final String ERROR_409_CORREO = """
			{"timestamp": "2026-11-08T23:10:00.000000Z", "status": 409, "error": "Conflict", \
			"codigo": "correo_duplicado", "message": "Ya hay un usuario con ese correo", \
			"path": "/api/v1/admin/usuarios", "details": [{"field": "correo", "message": "ya está registrado"}]}""";

	static final String ERROR_409_ULTIMO_ADMIN = """
			{"timestamp": "2026-11-08T23:10:00.000000Z", "status": 409, "error": "Conflict", \
			"codigo": "ultimo_admin", "message": "No se puede quitar al último administrador activo", \
			"path": "/api/v1/admin/usuarios/5b0c1f3e-7d0b-4f5e-9a39-2f0f5e8f6a11", "details": []}""";

	static final String ERROR_429_LOGIN = """
			{"timestamp": "2026-11-08T23:10:00.000000Z", "status": 429, "error": "Too Many Requests", \
			"codigo": "demasiados_intentos", \
			"message": "Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo", \
			"path": "/api/v1/auth/login", "details": []}""";

	static final String SALA_RESUMEN = """
			{"id": "0b6c0b0e-8f6e-4c53-b8f1-6f3a2e6f9b21", "codigo": "BACALAR", "estado": "activa", \
			"escena_actual": "faro", "puzzles_resueltos": 1, "puzzles_total": 4, "app_unida": true, \
			"dispositivos": {"juego": true, "app": true}, "creada_en": "2026-11-08T22:40:00.000000Z", \
			"ultima_actividad": "2026-11-08T23:05:00.000000Z", "expira_en": "2026-11-09T01:05:00.000000Z"}""";

	static final String SALAS_ADMIN = "{\"contenido\": [" + SALA_RESUMEN
			+ "], \"pagina\": 0, \"tamano\": 20, \"total\": 1, \"total_paginas\": 1}";

	static final String SALA_ADMIN = """
			{
			  "id": "0b6c0b0e-8f6e-4c53-b8f1-6f3a2e6f9b21",
			  "codigo": "BACALAR",
			  "estado": "activa",
			  "escena_actual": "faro",
			  "puzzles": [
			    {"puzzle": "radio", "estado": "resuelto", "datos": {"sintonizada": true}},
			    {"puzzle": "photo", "estado": "disponible", "datos": {}},
			    {"puzzle": "lookout", "estado": "bloqueado", "datos": {}},
			    {"puzzle": "observatory", "estado": "bloqueado", "datos": {}}
			  ],
			  "descubrimientos": [
			    {"clave": "pescador_dialogo_completo", "datos": {}, "origen": "juego", \
			"creado_en": "2026-11-08T22:50:00.000000Z"}
			  ],
			  "dispositivos": {"juego": true, "app": true},
			  "app_unida": true,
			  "creada_en": "2026-11-08T22:40:00.000000Z",
			  "ultima_actividad": "2026-11-08T23:05:00.000000Z",
			  "expira_en": "2026-11-09T01:05:00.000000Z",
			  "eventos": [
			    {"tipo": "sala.creada", "origen": "juego", "payload": {"codigo": "BACALAR"}, "creado_en": "2026-11-08T22:40:00.000000Z"}
			  ]
			}""";

	static final String ERROR_400_ESTADO = """
			{"timestamp": "2026-11-08T23:10:00.000000Z", "status": 400, "error": "Bad Request", "codigo": null, \
			"message": "Valor inválido para 'estado'", "path": "/api/v1/admin/salas", "details": []}""";

	static final String ERROR_404_SALA = """
			{"timestamp": "2026-11-08T23:10:00.000000Z", "status": 404, "error": "Not Found", "codigo": null, \
			"message": "No existe la sala 0b6c0b0e-8f6e-4c53-b8f1-6f3a2e6f9b21", \
			"path": "/api/v1/admin/salas/0b6c0b0e-8f6e-4c53-b8f1-6f3a2e6f9b21", "details": []}""";

}
