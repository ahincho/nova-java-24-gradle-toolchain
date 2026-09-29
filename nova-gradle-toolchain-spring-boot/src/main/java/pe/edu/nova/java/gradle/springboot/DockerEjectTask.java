package pe.edu.nova.java.gradle.springboot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * Escribe el Dockerfile de la plataforma en la raíz del repositorio, para un pipeline que lo exige
 * ahí. Lo normal es no hacerlo: una copia envejece sin fallar.
 *
 * <p>Reemplaza un Dockerfile que escribió antes, pero nunca uno que no es de Nova.
 */
@UntrackedTask(because = "It writes into the repository on request")
public abstract class DockerEjectTask extends DefaultTask {

    /** Crea la tarea; la instancia Gradle. */
    public DockerEjectTask() {}

    /**
     * Dónde se escribe.
     *
     * @return la propiedad
     */
    @OutputFile
    public abstract RegularFileProperty getDockerfile();

    /** Escribe el Dockerfile. */
    @TaskAction
    public void eject() {
        Path dockerfile = getDockerfile().get().getAsFile().toPath();
        try {
            if (Files.exists(dockerfile)
                    && !Files.readString(dockerfile, StandardCharsets.UTF_8).contains(NovaDockerfile.MARKER)) {
                throw new GradleException(
                        "nova: ya hay un Dockerfile que no es de Nova en " + dockerfile + "; no se reemplaza");
            }
            String version = Objects.requireNonNullElse(
                    DockerEjectTask.class.getPackage().getImplementationVersion(), "sin versión");
            Files.writeString(dockerfile, NovaDockerfile.ejected(version), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("nova: no se pudo escribir " + dockerfile, e);
        }
        getLogger().lifecycle("nova: Dockerfile escrito en {}", dockerfile);
    }
}
