# Vestigia backend

Backend de **Vestigia**, el proyecto integrador del grupo IDGS101N de la Universidad Tecnológica de Chihuahua. Vestigia es un videojuego de misterio para PC (Unity) con una app móvil (Flutter) que funciona como la libreta de la protagonista. El juego y la app no se hablan entre sí. Los dos usan este backend por REST y WebSocket, y el backend guarda el estado de cada sala en PostgreSQL.

El plan por pasos y las decisiones están en [`docs/PLAN.md`](docs/PLAN.md). Las reglas para trabajar en el repo están en [`CLAUDE.md`](CLAUDE.md).

## Stack

- Java 21 y Spring Boot 4.1.1, compilado con Maven a través del wrapper `./mvnw`.
- PostgreSQL. Flyway crea el esquema al arrancar con las migraciones de `src/main/resources/db/migration/`.
- WebSocket simple en `/ws`, sin STOMP ni SockJS.
- Swagger UI con springdoc en `/swagger-ui/index.html`.
- Pruebas de integración contra un Postgres real con Testcontainers.

## Levantarlo en local

Necesitas Java 21 y Docker.

1. Arranca un Postgres con los valores de desarrollo por defecto:

	```sh
	docker run -d --name vestigia-pg -e POSTGRES_DB=vestigia -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:17-alpine
	```

2. Arranca el backend desde la raíz del repo:

	```sh
	./mvnw spring-boot:run
	```

3. Comprueba que responde:

	```sh
	curl http://localhost:8080/actuator/health
	```

	La respuesta incluye `"status":"UP"`.

Si el puerto 5432 ya está ocupado, publica Postgres en otro puerto (`-p 55432:5432`) y arranca con `DB_URL=jdbc:postgresql://localhost:55432/vestigia ./mvnw spring-boot:run`.

Si tu Postgres usa otras credenciales, copia `application-local.properties.example` como `application-local.properties` en la raíz del repo y edítalo. Git ignora ese archivo.

## Variables de entorno

| Variable | Para qué | Valor de desarrollo |
|---|---|---|
| `DB_URL` | URL JDBC de Postgres, con la forma `jdbc:postgresql://host:puerto/base` | `jdbc:postgresql://localhost:5432/vestigia` |
| `DB_USER` | Usuario de Postgres | `postgres` |
| `DB_PASSWORD` | Contraseña de Postgres | `postgres` |
| `PORT` | Puerto HTTP del servidor | `8080` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos para `/api/**`, separados por comas | `http://localhost:5173,http://localhost:4173` |

Cada valor se busca primero en las variables de entorno, luego en `application-local.properties` y al final se usa el valor de desarrollo. Por eso `application-local.properties` declara `DB_URL`, `DB_USER` y `DB_PASSWORD`, no `spring.datasource.*`. Con `spring.datasource.*`, el archivo local taparía a las variables de entorno.

No subas contraseñas, tokens ni URLs con credenciales al repo.

## Correr las pruebas

- `./mvnw test` corre las pruebas unitarias y las de `@WebMvcTest`. No necesita Docker.
- `./mvnw verify` corre además las clases `*IT` con maven-failsafe. Esas pruebas arrancan un `postgres:17-alpine` con Testcontainers, así que Docker tiene que estar corriendo.

## Construir y correr la imagen Docker

El `Dockerfile` compila en una etapa con JDK 21 y corre el JAR en otra con JRE 21, con el usuario sin privilegios `vestigia`. La imagen no corre las pruebas.

```sh
docker build -t vestigia-backend .
docker run --rm -p 8080:8080 \
	-e DB_URL=jdbc:postgresql://host.docker.internal:5432/vestigia \
	-e DB_USER=postgres -e DB_PASSWORD=postgres \
	vestigia-backend
```

`host.docker.internal` apunta a tu máquina en Docker Desktop. En Linux, pon el contenedor del backend y el de Postgres en la misma red de Docker y usa el nombre del contenedor de Postgres como host.

## Despliegue

El backend se despliega en Railway desde `main`. Los pasos y las variables están en [`docs/despliegue.md`](docs/despliegue.md).
