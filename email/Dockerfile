# ── Build ────────────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ── Runtime ──────────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S samec && adduser -S samec -G samec
COPY --from=build --chown=samec:samec /build/target/*.jar app.jar
USER samec
# 256m de heap dentro de un mem_limit de 512m: deja margen a metaspace, hilos y los buffers
# off-heap de Kafka (con menos margen, en Auditorías el cgroup mataba el proceso por OOM).
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
