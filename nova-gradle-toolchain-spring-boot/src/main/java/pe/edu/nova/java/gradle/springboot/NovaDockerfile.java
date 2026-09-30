package pe.edu.nova.java.gradle.springboot;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Los Dockerfiles de la plataforma, empaquetados en el plugin. No se copian a cada repositorio. */
final class NovaDockerfile {

    /** La marca que identifica un Dockerfile de Nova. */
    static final String MARKER = "Nova Platform: la imagen de un servicio Spring Boot";

    /** La plantilla de la imagen de la JVM. */
    static final String JVM = "Dockerfile";

    /** La plantilla de la imagen nativa (ADR-045). */
    static final String NATIVE = "Dockerfile.native";

    private NovaDockerfile() {}

    static String content() {
        return content(JVM);
    }

    static String content(String template) {
        try (InputStream in = NovaDockerfile.class.getResourceAsStream(template)) {
            if (in == null) {
                throw new IllegalStateException("The Nova toolchain jar does not contain " + template);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + template + " from the Nova toolchain jar", e);
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
