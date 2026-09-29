package pe.edu.nova.java.gradle.springboot;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** El Dockerfile de la plataforma, empaquetado en el plugin. No se copia a cada repositorio. */
final class NovaDockerfile {

    /** La marca que identifica un Dockerfile de Nova. */
    static final String MARKER = "Nova Platform: la imagen de un servicio Spring Boot";

    private NovaDockerfile() {}

    static String content() {
        try (InputStream in = NovaDockerfile.class.getResourceAsStream("Dockerfile")) {
            if (in == null) {
                throw new IllegalStateException("The Nova toolchain jar does not contain its Dockerfile");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the Nova Dockerfile", e);
        }
    }

    /**
     * El Dockerfile para escribir en un repositorio: con un encabezado que dice de dónde salió,
     * debajo de {@code # syntax=}, que solo cuenta si es la primera línea.
     *
     * @param version la versión del toolchain que lo escribió
     * @return el contenido
     */
    static String ejected(String version) {
        String content = content();
        int firstLine = content.indexOf('\n') + 1;
        return content.substring(0, firstLine)
                + "# Escrito por novaDockerEject, del toolchain de Nova " + version + ". Para actualizarlo, se\n"
                + "# vuelve a correr novaDockerEject; una edición a mano se pierde en la próxima.\n"
                + "# Se construye desde la raíz: docker build --build-arg JAR_FILE=build/libs/<jar> .\n"
                + content.substring(firstLine);
    }
}
