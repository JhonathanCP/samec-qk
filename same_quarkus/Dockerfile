# ── Build ────────────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# Capa de dependencias aparte: si solo cambia el código, no se vuelven a descargar.
RUN mvn -B -q quarkus:go-offline dependency:go-offline
COPY src ./src
# El tipo de BD se fija al compilar (Quarkus genera el dialecto en build): oracle | postgresql.
ARG SAMEC_DB_KIND=oracle
ENV SAMEC_DB_KIND=${SAMEC_DB_KIND}
RUN mvn -B -q package -DskipTests

# ── Runtime ──────────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S samec && adduser -S samec -G samec
COPY --from=build --chown=samec:samec /build/target/quarkus-app/ /app/
USER samec
EXPOSE 8753
# Heap por debajo del mem_limit de docker-compose (1g): deja margen para metaspace, hilos y buffers.
ENTRYPOINT ["java", "-Xmx640m", "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", "-jar", "quarkus-run.jar"]
