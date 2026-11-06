package com.vestigia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.vestigia.ws.SalaWebSocketHandler;

/** WebSocket simple en /ws, sin STOMP ni SockJS. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final SalaWebSocketHandler handler;

	private final int maxMensaje;

	public WebSocketConfig(SalaWebSocketHandler handler,
			@Value("${vestigia.ws.mensaje-max-caracteres}") int maxMensaje) {
		this.handler = handler;
		this.maxMensaje = maxMensaje;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler, "/ws");
	}

	/** Tomcat cierra con 1009 un mensaje de texto de más caracteres que esto (o uno binario de más bytes). */
	@Bean
	public ServletServerContainerFactoryBean createWebSocketContainer() {
		ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
		container.setMaxTextMessageBufferSize(maxMensaje);
		container.setMaxBinaryMessageBufferSize(maxMensaje);
		return container;
	}

}
