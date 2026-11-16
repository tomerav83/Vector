# Vector in a container: the reviewer needs Docker and a model key, not Java or Maven. `docker compose up` (docker-compose.yml).

# Build: compiling generates jOOQ's classes, which installs DuckDB's spatial extension into ~/.duckdb.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --create-home vector
USER vector
WORKDIR /home/vector/app
# The spatial extension the build installed, so the first start downloads only the data.
COPY --from=build --chown=vector /root/.duckdb /home/vector/.duckdb
COPY --from=build --chown=vector /src/target/vector-*.jar app.jar
COPY --chown=vector data/sources.lock.json data/
# The downloads' volume mounts here; made now so it belongs to vector.
RUN mkdir data/cache
# Inside the container every address; docker-compose.yml publishes the ports on the host's 127.0.0.1 only.
ENV SERVER_ADDRESS=0.0.0.0 MANAGEMENT_SERVER_ADDRESS=0.0.0.0
EXPOSE 8080 8081
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
