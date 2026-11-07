package com.vestigia;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Peticiones HTTP reales contra la app levantada en un puerto aleatorio. */
public class ClienteApi {

	private final HttpClient http = HttpClient.newHttpClient();

	private final int port;

	private final JsonMapper json;

	public ClienteApi(int port, JsonMapper json) {
		this.port = port;
		this.json = json;
	}

	public HttpResponse<String> crear(String codigo) throws Exception {
		return enviar("POST", "/api/v1/salas", "{\"codigo\":\"" + codigo + "\"}", null);
	}

	public String tokenJuego(String codigo) throws Exception {
		return token(crear(codigo));
	}

	public String tokenApp(String codigo) throws Exception {
		return token(enviar("POST", "/api/v1/salas/" + codigo + "/unirse", "", null));
	}

	public HttpResponse<String> enviar(String metodo, String ruta, String cuerpo, String token) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta))
				.header("Content-Type", "application/json")
				.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(cuerpo));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	public JsonNode cuerpo(HttpResponse<String> respuesta) {
		return json.readTree(respuesta.body());
	}

	private String token(HttpResponse<String> respuesta) {
		return cuerpo(respuesta).get("token").asString();
	}

	/** Lanza la tarea en varios hilos que arrancan a la vez y devuelve sus resultados. */
	public static <T> List<T> enParalelo(int hilos, Callable<T> tarea) throws Exception {
		CountDownLatch salida = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(hilos);
		try {
			List<Future<T>> futuros = new ArrayList<>();
			for (int i = 0; i < hilos; i++) {
				futuros.add(executor.submit(() -> {
					salida.await();
					return tarea.call();
				}));
			}
			salida.countDown();
			List<T> resultados = new ArrayList<>();
			for (Future<T> futuro : futuros) {
				resultados.add(futuro.get());
			}
			return resultados;
		}
		finally {
			executor.shutdownNow();
		}
	}

}
