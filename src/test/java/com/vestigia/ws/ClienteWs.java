package com.vestigia.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Un cliente WebSocket real (java.net.http) contra /ws que guarda lo que recibe. */
class ClienteWs implements WebSocket.Listener {

	record Cierre(int codigo, String razon) {
	}

	private static final HttpClient HTTP = HttpClient.newHttpClient();

	private final BlockingQueue<JsonNode> mensajes = new LinkedBlockingQueue<>();

	private final CompletableFuture<Cierre> cierre = new CompletableFuture<>();

	private final AtomicInteger pings = new AtomicInteger();

	private final StringBuilder parcial = new StringBuilder();

	private final JsonMapper json;

	private WebSocket ws;

	private ClienteWs(JsonMapper json) {
		this.json = json;
	}

	static ClienteWs abrir(int port, JsonMapper json) throws Exception {
		return abrir(port, json, null);
	}

	/** Abre la conexión con el encabezado Origin, como un navegador, si {@code origin} no es nulo. */
	static ClienteWs abrir(int port, JsonMapper json, String origin) throws Exception {
		ClienteWs cliente = new ClienteWs(json);
		WebSocket.Builder builder = HTTP.newWebSocketBuilder();
		if (origin != null) {
			builder.header("Origin", origin);
		}
		cliente.ws = builder.buildAsync(URI.create("ws://localhost:" + port + "/ws"), cliente).get(5, TimeUnit.SECONDS);
		return cliente;
	}

	/** Abre una conexión, manda auth con el token y devuelve el auth.ok. */
	static ClienteWs autenticado(int port, JsonMapper json, String token) throws Exception {
		ClienteWs cliente = abrir(port, json);
		JsonNode authOk = cliente.autenticar(token);
		assertThat(authOk.get("type").asString()).isEqualTo("auth.ok");
		return cliente;
	}

	JsonNode autenticar(String token) throws Exception {
		enviar("{\"type\":\"auth\",\"payload\":{\"token\":\"" + token + "\"}}");
		return siguiente();
	}

	void enviar(String texto) throws Exception {
		ws.sendText(texto, true).get(5, TimeUnit.SECONDS);
	}

	void enviarBinario(byte[] bytes) throws Exception {
		ws.sendBinary(ByteBuffer.wrap(bytes), true).get(5, TimeUnit.SECONDS);
	}

	/** El siguiente mensaje recibido, esperando hasta 5 s. */
	JsonNode siguiente() throws InterruptedException {
		JsonNode mensaje = mensajes.poll(5, TimeUnit.SECONDS);
		assertThat(mensaje).as("un mensaje antes de 5 s").isNotNull();
		return mensaje;
	}

	/** Comprueba que no llega ningún mensaje durante ese tiempo. */
	void sinMensajes(Duration espera) throws InterruptedException {
		assertThat(mensajes.poll(espera.toMillis(), TimeUnit.MILLISECONDS)).as("ningún mensaje").isNull();
	}

	Cierre cierre(Duration espera) throws Exception {
		return cierre.get(espera.toMillis(), TimeUnit.MILLISECONDS);
	}

	Cierre cierre() throws Exception {
		return cierre(Duration.ofSeconds(5));
	}

	boolean cerrado() {
		return cierre.isDone();
	}

	int pings() {
		return pings.get();
	}

	/** Cierra desde el cliente y espera la respuesta del servidor. */
	void cerrar() throws Exception {
		ws.sendClose(WebSocket.NORMAL_CLOSURE, "").get(5, TimeUnit.SECONDS);
		cierre();
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		parcial.append(data);
		if (last) {
			mensajes.add(json.readTree(parcial.toString()));
			parcial.setLength(0);
		}
		webSocket.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
		pings.incrementAndGet();
		webSocket.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		cierre.complete(new Cierre(statusCode, reason));
		return null;
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		cierre.completeExceptionally(error);
	}

}
