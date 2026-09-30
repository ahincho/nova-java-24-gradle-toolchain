# syntax=docker/dockerfile:1.7
# Nova Platform: la imagen de un servicio Spring Boot (ADR-044). La construye novaDocker.
#
#   ./gradlew novaDocker
#   ./gradlew novaDocker --tag=plaza-orders:dev --build-arg=JAVA_VERSION=25
#
# **Una sola imagen para todos los ambientes.** No hay nada de dev, qa ni prod adentro: el ambiente
# llega como variable de entorno al arrancar. El artefacto que se probó en dev es el que llega a
# prod, byte por byte. Es la misma regla que la imagen de NestJS (ADR-027).
#
# No se copia a cada repositorio: vive en el toolchain, porque una copia envejece sin fallar. Para un
# pipeline que exige el archivo en la raíz está novaDockerEject.

# Las imágenes base como ARG, para fijarlas por digest sin perder la variable:
#
#   ./gradlew novaDocker --build-arg=JAVA_IMAGE=gcr.io/distroless/java25-debian13@sha256:...
#
# Un tag cambia con el tiempo, así que dos builds del mismo commit pueden dar imágenes distintas. El
# digest es lo que hace el build reproducible y deja auditar qué base había el día de un incidente.
#
# La imagen final es distroless (ADR-046): el JRE Temurin sin shell ni gestor de paquetes, y se
# descarga sin cuenta. Docker Hardened Images es la otra opción, con la variante JRE 25 de
# dhi.io/eclipse-temurin en JAVA_IMAGE; pide antes docker login dhi.io. Para volver a la imagen de
# Ubuntu: --build-arg=JAVA_IMAGE=eclipse-temurin:25-jre.
#
# La etapa que abre el jar corre un RUN, que necesita una shell, así que usa la imagen completa de
# Temurin. No llega a la imagen final.
ARG JAVA_VERSION=25
ARG BUILD_IMAGE=eclipse-temurin:${JAVA_VERSION}-jre
ARG JAVA_IMAGE=gcr.io/distroless/java${JAVA_VERSION}-debian13

# ---------------------------------------------------------------------------
# layers: abre el jar por capas
# ---------------------------------------------------------------------------
# Las dependencias cambian poco y la aplicación en cada commit. Separadas, una imagen nueva reusa las
# capas de dependencias de la anterior y solo sube la de la aplicación.
FROM ${BUILD_IMAGE} AS layers
WORKDIR /workspace
ARG JAR_FILE
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

# ---------------------------------------------------------------------------
# runtime: el JRE y las capas, de la más estable a la que más cambia
# ---------------------------------------------------------------------------
FROM ${JAVA_IMAGE} AS runtime
WORKDIR /application
COPY --from=layers /workspace/extracted/dependencies/ ./
COPY --from=layers /workspace/extracted/spring-boot-loader/ ./
COPY --from=layers /workspace/extracted/snapshot-dependencies/ ./
COPY --from=layers /workspace/extracted/application/ ./

# Un usuario sin privilegios: los archivos son de root y solo se leen.
USER 10001:10001

# El puerto por convención de la plataforma para los servicios Java.
EXPOSE 8080

# java directo y en forma exec: el proceso queda como PID 1 y recibe el SIGTERM del orquestador, así
# el apagado ordenado de Spring Boot ocurre. El heap se ajusta a la memoria del contenedor.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
