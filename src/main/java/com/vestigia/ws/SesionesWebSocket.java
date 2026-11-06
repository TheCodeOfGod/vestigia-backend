package com.vestigia.ws;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.vestigia.dto.Sala;
import com.vestigia.service.SalaService;
import com.vestigia.service.SalaService.Credencial;
import com.vestigia.service.SalaService.Rol;

import tools.jackson.databind.json.JsonMapper;

/**
 * Las conexiones autenticadas, en memoria: como mucho una por sala y rol. Todos los métodos comparten un solo candado,
 * así que registrar, avisar la presencia, emitir y cerrar una sala nunca se intercalan: un cliente recibe auth.ok antes
 * que cualquier otro mensaje y una sala cerrada no conserva conexiones. Solo sirve con una instancia del backend.
 */
@Component
public class SesionesWebSocket implements SalaService.Presencia {

	static final Set<Rol> TODOS = Set.of(Rol.values());

	private static final Logger log = LoggerFactory.getLogger(SesionesWebSocket.class);

	private record Conexion(Credencial credencial, WebSocketSession sesion) {
	}

	private final Map<String, Conexion> porSesion = new HashMap<>();

	private final Map<UUID, Map<Rol, Conexion>> porSala = new HashMap<>();

	private final JsonMapper json;

	public SesionesWebSocket(JsonMapper json) {
		this.json = json;
	}

	/**
	 * Autentica la sesión con {@code autenticar} y, si el token sirve, la registra, le manda auth.ok y avisa
	 * dispositivo.conectado al otro rol. Autenticar dentro del candado impide que el cierre de la sala se cuele entre
	 * validar el token y registrar la sesión, lo que dejaría una conexión viva en una sala cerrada. Una conexión previa
	 * del mismo rol se cierra con {@link Cierre#REEMPLAZADA}; como el rol sigue conectado, no se avisa desconectado.
	 */
	public synchronized Optional<Credencial> conectar(WebSocketSession sesion,
			Supplier<Optional<Credencial>> autenticar) {
		if (!sesion.isOpen()) {
			return Optional.empty();
		}
		Optional<Credencial> credencial = autenticar.get();
		credencial.ifPresent(c -> {
			Conexion conexion = new Conexion(c, sesion);
			Conexion anterior = porSala.computeIfAbsent(c.salaId(), id -> new EnumMap<>(Rol.class)).put(c.rol(), conexion);
			porSesion.put(sesion.getId(), conexion);
			log.info("[WS] conectado sala={} rol={}{}", c.codigo(), c.rol(), anterior == null ? "" : " (reemplaza otra)");
			enviar(conexion, new Mensaje("auth.ok", Map.of("rol", c.rol().name(), "sala", c.codigo())));
			enviar(c.salaId(), Set.of(otro(c.rol())), new Mensaje("dispositivo.conectado", Map.of("rol", c.rol().name())));
			if (anterior != null) {
				porSesion.remove(anterior.sesion().getId());
				cerrar(anterior.sesion(), Cierre.REEMPLAZADA.status());
			}
		});
		return credencial;
	}

	public synchronized boolean autenticada(WebSocketSession sesion) {
		return porSesion.containsKey(sesion.getId());
	}

	/** Cierra la sesión si a estas alturas no se autenticó. Devuelve si la cerró. */
	public synchronized boolean cerrarSiNoSeAutentico(WebSocketSession sesion, CloseStatus status) {
		if (porSesion.containsKey(sesion.getId()) || !sesion.isOpen()) {
			return false;
		}
		cerrar(sesion, status);
		return true;
	}

	/** Responde a una sesión autenticada, por ejemplo con un error. */
	public synchronized void responder(WebSocketSession sesion, Mensaje mensaje) {
		Conexion conexion = porSesion.get(sesion.getId());
		if (conexion != null) {
			enviar(conexion, mensaje);
		}
	}

	/**
	 * Olvida la sesión que se cerró. Si era la conexión vigente de su rol, avisa dispositivo.desconectado al otro rol;
	 * si otra conexión ya la había reemplazado, no avisa nada.
	 */
	public synchronized void desconectar(WebSocketSession sesion) {
		Conexion conexion = porSesion.remove(sesion.getId());
		if (conexion == null) {
			return;
		}
		Credencial c = conexion.credencial();
		Map<Rol, Conexion> sala = porSala.get(c.salaId());
		if (sala == null || !sala.remove(c.rol(), conexion)) {
			return;
		}
		if (sala.isEmpty()) {
			porSala.remove(c.salaId());
		}
		log.info("[WS] desconectado sala={} rol={}", c.codigo(), c.rol());
		enviar(c.salaId(), Set.of(otro(c.rol())), new Mensaje("dispositivo.desconectado", Map.of("rol", c.rol().name())));
	}

	/** Manda el mensaje a las conexiones de la sala con alguno de esos roles. */
	public synchronized void enviar(UUID salaId, Set<Rol> destinos, Mensaje mensaje) {
		for (Conexion conexion : List.copyOf(porSala.getOrDefault(salaId, Map.of()).values())) {
			if (destinos.contains(conexion.credencial().rol())) {
				enviar(conexion, mensaje);
			}
		}
	}

	/** Manda el aviso a toda la sala, la olvida y cierra sus conexiones con {@link Cierre#SALA_CERRADA}. */
	public synchronized void cerrarSala(UUID salaId, Mensaje aviso) {
		Map<Rol, Conexion> sala = porSala.remove(salaId);
		if (sala == null) {
			return;
		}
		List<Conexion> conexiones = new ArrayList<>(sala.values());
		for (Conexion conexion : conexiones) {
			porSesion.remove(conexion.sesion().getId());
			enviar(conexion, aviso);
		}
		for (Conexion conexion : conexiones) {
			cerrar(conexion.sesion(), Cierre.SALA_CERRADA.status());
		}
	}

	@Override
	public synchronized Sala.Dispositivos dispositivos(UUID salaId) {
		Map<Rol, Conexion> sala = porSala.getOrDefault(salaId, Map.of());
		return new Sala.Dispositivos(sala.containsKey(Rol.juego), sala.containsKey(Rol.app));
	}

	/** Un ping a cada conexión autenticada mantiene abiertos los proxies y NAT que cortan conexiones inactivas. */
	@Scheduled(fixedRateString = "${vestigia.ws.ping}")
	public synchronized void ping() {
		for (Conexion conexion : List.copyOf(porSesion.values())) {
			try {
				conexion.sesion().sendMessage(new PingMessage());
			}
			catch (IOException | RuntimeException ex) {
				log.warn("[WS] no se pudo mandar el ping sala={} rol={}: {}", conexion.credencial().codigo(),
						conexion.credencial().rol(), ex.getClass().getSimpleName());
			}
		}
	}

	/** El mensaje en JSON. Un envío fallido solo se registra: la conexión rota se limpia cuando el servidor la cierre. */
	private void enviar(Conexion conexion, Mensaje mensaje) {
		try {
			conexion.sesion().sendMessage(new TextMessage(json.writeValueAsString(mensaje)));
		}
		catch (IOException | RuntimeException ex) {
			log.warn("[WS] no se pudo mandar {} a sala={} rol={}: {}", mensaje.type(), conexion.credencial().codigo(),
					conexion.credencial().rol(), ex.getClass().getSimpleName());
		}
	}

	static void cerrar(WebSocketSession sesion, CloseStatus status) {
		try {
			sesion.close(status);
		}
		catch (IOException | RuntimeException ex) {
			log.warn("[WS] no se pudo cerrar la sesión {}: {}", sesion.getId(), ex.getClass().getSimpleName());
		}
	}

	private static Rol otro(Rol rol) {
		return rol == Rol.juego ? Rol.app : Rol.juego;
	}

}
