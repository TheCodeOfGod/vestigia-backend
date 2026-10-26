FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Las pruebas corren en CI con ./mvnw verify; la imagen solo empaqueta.
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system vestigia && useradd --system --gid vestigia --no-create-home vestigia
COPY --from=build /app/target/*.jar app.jar
USER vestigia
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
