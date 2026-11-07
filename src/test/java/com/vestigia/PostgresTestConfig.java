package com.vestigia;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Un solo Postgres y un solo reloj para todas las *IT. Las clases que importan esta configuración comparten el contexto
 * de Spring, así que quien adelante el reloj debe reiniciarlo al terminar.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("postgres:17-alpine");
	}

	@Bean
	@Primary
	RelojDePrueba relojDePrueba() {
		return new RelojDePrueba();
	}

}
