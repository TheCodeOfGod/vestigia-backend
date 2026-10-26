package com.vestigia;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Un solo Postgres para todas las *IT. Las clases que importan esta configuración comparten el contexto de Spring. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("postgres:17-alpine");
	}

}
