package com.vestigia.ws;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import com.vestigia.dto.Sala;
import com.vestigia.service.SalaService;
import com.vestigia.service.SalaService.Credencial;
import com.vestigia.service.SalaService.Rol;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las conexiones autenticadas, en memoria: como mucho una por sala y rol. Solo sirve con una instancia del backend.
 * <p>
 * Un candado ordena los cambios del registro y la cola de salida de cada conexión: registrar, avisar la presencia,
 * emitir y cerrar una sala nunca se intercalan, así que auth.ok es lo primero que recibe un cliente y una sala cerrada
 * no conserva conexiones. Dentro del candado solo se trabaja en memoria: los envíos los hace después un hilo virtual por
 * conexión, en el orden de su cola, y la base de datos se consulta antes de tomarlo. Un cliente que no lee solo atasca
 * su propia cola: Tomcat corta el envío bloqueado tras {@code vestigia.ws.envio-limite} y una cola de más de
 * {@code vestigia.ws.envio-max-bytes} cierra la conexión con {@link Cierre#CLIENTE_LENTO}.
 */
@Component
public class SesionesWebSocket implements SalaService.Presencia {

	static final Set<Rol> TODOS = Set.of(Rol.values());

	/**
	 * Cuánto se recuerda que una sala se cerró. Basta con cubrir el instante entre validar un token en la base y
	 * registrar la conexión; los ids de sala no se reutilizan, así que olvidarla después no reabre nada.
	 */
	private static final Duration RECUERDO_CIERRES = Duration.ofMinutes(10);

	private static final Logger log = LoggerFactory.getLogger(SesionesWebSocket.class);

	private final Map<String, Conexion> porSesion = new ConcurrentHashMap<>();

	private final Map<UUID, Map<Rol, Conexion>> porSala = new ConcurrentHashMap<>();

	/** Salas cerradas hace poco, con el momento del cierre en nanoTime. Solo se usa dentro del candado. */
	private final LinkedHashMap<UUID, Long> cerradas = new LinkedHashMap<>();

	/** Sesiones sin autenticar que el servidor ya decidió cerrar: un auth que llegue tarde no las registra. */
	private final Set<String> rechazadas = ConcurrentHashMap.newKeySet();

	private final JsonMapper json;

	private final Executor envios;

	private final long maxBytesPendientes;

	@Autowired
	public SesionesWebSocket(JsonMapper json, @Value("${vestigia.ws.envio-max-bytes}") long maxBytesPendientes) {
		this(json, maxBytesPendientes, Executors.newVirtualThreadPerTaskExecutor());
	}

	SesionesWebSocket(JsonMapper json, long maxBytesPendientes, Executor envios) {
		this.json = json;
		this.maxBytesPendientes = maxBytesPendientes;
		this.envios = envios;
	}

	@PreDestroy
	void detener() {
		if (envios instanceof ExecutorService servicio) {
			servicio.shutdownNow();
		}
	}

	/**
	 * Registra la sesión con la credencial que ya validó la base, le encola auth.ok y avisa dispositivo.conectado al
	 * otro rol. Devuelve false, sin registrar, si la sesión ya se cerró o si su sala se cerró después de validar el
	 * token, o si la sesión ya estaba registrada. Una conexión previa del mismo rol se cierra con
	 * {@link Cierre#REEMPLAZADA}; como el rol sigue conectado, no se avisa desconectado.
	 */
	public boolean conectar(WebSocketSession sesion, Credencial credencial) {
		synchronized (this) {
			if (!sesion.isOpen() || porSesion.containsKey(sesion.getId()) || rechazadas.contains(sesion.getId())
					|| cerradas.containsKey(credencial.salaId())) {
				return false;
			}
			Conexion conexion = new Conexion(credencial, sesion);
			Conexion anterior = porSala.computeIfAbsent(credencial.salaId(), id -> new ConcurrentHashMap<>())
					.put(credencial.rol(), conexion);
			porSesion.put(sesion.getId(), conexion);
			log.info("[WS] conectado sala={} rol={}{}", credencial.codigo(), credencial.rol(),
					anterior == null ? "" : " (reemplaza otra)");
			conexion.encolar(texto(new Mensaje("auth.ok", Map.of("rol", credencial.rol().name(), "sala",
					credencial.codigo()))));
			encolar(credencial.salaId(), Set.of(otro(credencial.rol())),
					texto(new Mensaje("dispositivo.conectado", Map.of("rol", credencial.rol().name()))));
			if (anterior != null) {
				anterior.cerrar(Cierre.REEMPLAZADA.status());
			}
			return true;
		}
	}

	/**
	 * Si la sesión se autenticó. Sigue siendo true para una conexión que el servidor ya mandó cerrar (sala cerrada o
	 * reemplazada) hasta que Tomcat la cierra: así un mensaje que llegue en ese hueco recibe la respuesta de una sesión
	 * autenticada, que se descarta, y no la ruta de auth, que la cerraría desde otro hilo con otro código.
	 */
	public boolean autenticada(WebSocketSession sesion) {
		return porSesion.containsKey(sesion.getId());
	}

	/** Cierra la sesión si a estas alturas no se autenticó. Devuelve si la cerró. */
	public boolean cerrarSiNoSeAutentico(WebSocketSession sesion, CloseStatus status) {
		synchronized (this) {
			if (porSesion.containsKey(sesion.getId()) || !sesion.isOpen() || !rechazadas.add(sesion.getId())) {
				return false;
			}
		}
		envios.execute(() -> cerrar(sesion, status));
		return true;
	}

	/** Responde a una sesión autenticada, por ejemplo con un error. */
	public void responder(WebSocketSession sesion, Mensaje mensaje) {
		TextMessage texto = texto(mensaje);
		synchronized (this) {
			Conexion conexion = porSesion.get(sesion.getId());
			if (conexion != null) {
				conexion.encolar(texto);
			}
		}
	}

	/**
	 * Olvida la sesión que se cerró y descarta lo que quedaba en su cola, que ya no tiene a dónde ir. Si era la conexión
	 * vigente de su rol, avisa dispositivo.desconectado al otro rol; si otra conexión ya la había reemplazado o su sala
	 * se cerró, no avisa nada.
	 */
	public void desconectar(WebSocketSession sesion) {
		synchronized (this) {
			rechazadas.remove(sesion.getId());
			Conexion conexion = porSesion.remove(sesion.getId());
			if (conexion == null) {
				return;
			}
			conexion.descartar();
			Credencial c = conexion.credencial;
			Map<Rol, Conexion> sala = porSala.get(c.salaId());
			if (sala == null || !sala.remove(c.rol(), conexion)) {
				return;
			}
			if (sala.isEmpty()) {
				porSala.remove(c.salaId());
			}
			log.info("[WS] desconectado sala={} rol={}", c.codigo(), c.rol());
			encolar(c.salaId(), Set.of(otro(c.rol())),
					texto(new Mensaje("dispositivo.desconectado", Map.of("rol", c.rol().name()))));
		}
	}

	/** Encola el mensaje para las conexiones de la sala con alguno de esos roles. */
	public void enviar(UUID salaId, Set<Rol> destinos, Mensaje mensaje) {
		TextMessage texto = texto(mensaje);
		synchronized (this) {
			encolar(salaId, destinos, texto);
		}
	}

	/**
	 * Recuerda que la sala se cerró, para que ninguna conexión se registre en ella después, encola el aviso a toda la
	 * sala y luego el cierre de sus conexiones con {@link Cierre#SALA_CERRADA}. Las conexiones siguen en el registro,
	 * terminadas, hasta que Tomcat las cierra y llega {@link #desconectar}.
	 */
	public void cerrarSala(UUID salaId, Mensaje aviso) {
		TextMessage texto = texto(aviso);
		synchronized (this) {
			recordarCierre(salaId);
			Map<Rol, Conexion> sala = porSala.remove(salaId);
			if (sala == null) {
				return;
			}
			for (Conexion conexion : List.copyOf(sala.values())) {
				conexion.encolar(texto);
				conexion.cerrar(Cierre.SALA_CERRADA.status());
			}
		}
	}

	/** Se lee sin el candado: GET estado nunca espera a un envío ni a otra sala. */
	@Override
	public Sala.Dispositivos dispositivos(UUID salaId) {
		Map<Rol, Conexion> sala = porSala.getOrDefault(salaId, Map.of());
		return new Sala.Dispositivos(sala.containsKey(Rol.juego), sala.containsKey(Rol.app));
	}

	/** Un ping a cada conexión autenticada mantiene abiertos los proxies y NAT que cortan conexiones inactivas. */
	@Scheduled(fixedRateString = "${vestigia.ws.ping}")
	public void ping() {
		synchronized (this) {
			for (Conexion conexion : porSesion.values()) {
				conexion.encolar(new PingMessage());
			}
		}
	}

	private void encolar(UUID salaId, Set<Rol> destinos, TextMessage texto) {
		for (Conexion conexion : porSala.getOrDefault(salaId, Map.of()).values()) {
			if (destinos.contains(conexion.credencial.rol())) {
				conexion.encolar(texto);
			}
		}
	}

	private void recordarCierre(UUID salaId) {
		long ahora = System.nanoTime();
		cerradas.put(salaId, ahora);
		Iterator<Long> momentos = cerradas.values().iterator();
		while (momentos.hasNext() && ahora - momentos.next() > RECUERDO_CIERRES.toNanos()) {
			momentos.remove();
		}
	}

	private TextMessage texto(Mensaje mensaje) {
		return new TextMessage(json.writeValueAsString(mensaje));
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

	/** Lo que sale por una conexión, en orden: un mensaje o el cierre. */
	private record Salida(WebSocketMessage<?> mensaje, CloseStatus cierre) {
	}

	/**
	 * Una conexión autenticada y su cola de salida. {@link #encolar} nunca bloquea; {@link #vaciar} corre en un hilo
	 * virtual y escribe la cola en orden, con un solo escritor a la vez.
	 */
	private final class Conexion {

		private final Credencial credencial;

		private final WebSocketSession sesion;

		private final Queue<Salida> pendientes = new ConcurrentLinkedQueue<>();

		private final AtomicLong bytesPendientes = new AtomicLong();

		private final ReentrantLock escritor = new ReentrantLock();

		private volatile boolean terminada;

		Conexion(Credencial credencial, WebSocketSession sesion) {
			this.credencial = credencial;
			this.sesion = sesion;
		}

		void encolar(WebSocketMessage<?> mensaje) {
			if (terminada) {
				return;
			}
			if (bytesPendientes.addAndGet(mensaje.getPayloadLength()) > maxBytesPendientes) {
				log.warn("[WS] cliente lento sala={} rol={}: más de {} bytes sin enviar", credencial.codigo(),
						credencial.rol(), maxBytesPendientes);
				pendientes.clear();
				cerrar(Cierre.CLIENTE_LENTO.status());
				return;
			}
			pendientes.add(new Salida(mensaje, null));
			envios.execute(this::vaciar);
		}

		/** Encola el cierre: sale después de lo que ya está en la cola y descarta lo que llegue luego. */
		void cerrar(CloseStatus status) {
			if (terminada) {
				return;
			}
			terminada = true;
			pendientes.add(new Salida(null, status));
			envios.execute(this::vaciar);
		}

		/** La sesión ya se cerró: nada más sale por ella, ni otro cierre. */
		void descartar() {
			terminada = true;
			pendientes.clear();
		}

		private void vaciar() {
			while (!pendientes.isEmpty() && escritor.tryLock()) {
				try {
					Salida salida;
					while ((salida = pendientes.poll()) != null) {
						if (salida.cierre() != null) {
							pendientes.clear();
							SesionesWebSocket.cerrar(sesion, salida.cierre());
							return;
						}
						bytesPendientes.addAndGet(-salida.mensaje().getPayloadLength());
						sesion.sendMessage(salida.mensaje());
					}
				}
				catch (IOException | RuntimeException ex) {
					log.warn("[WS] no se pudo enviar a sala={} rol={}: {}", credencial.codigo(), credencial.rol(),
							ex.getClass().getSimpleName());
					terminada = true;
					pendientes.clear();
					SesionesWebSocket.cerrar(sesion, Cierre.CLIENTE_LENTO.status());
				}
				finally {
					escritor.unlock();
				}
			}
		}

	}

}
