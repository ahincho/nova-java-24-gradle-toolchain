# syntax=docker/dockerfile:1
# Nova Platform: la imagen de un servicio Spring Boot (ADR-044). La construye novaDocker.
#
# El jar se abre por capas en una etapa aparte, así una imagen nueva reusa las capas de dependencias
# de la anterior y solo cambia la de la aplicación. Corre con un usuario sin privilegios.
ARG JAVA_VERSION=25

FROM eclipse-temurin:${JAVA_VERSION}-jre AS layers
WORKDIR /workspace
ARG JAR_FILE
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:${JAVA_VERSION}-jre
WORKDIR /application
COPY --from=layers /workspace/extracted/dependencies/ ./
COPY --from=layers /workspace/extracted/spring-boot-loader/ ./
COPY --from=layers /workspace/extracted/snapshot-dependencies/ ./
COPY --from=layers /workspace/extracted/application/ ./
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
