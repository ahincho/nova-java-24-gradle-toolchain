package pe.edu.nova.java.gradle.springboot;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

/**
 * Construye la imagen del servicio con el Dockerfile de la plataforma.
 *
 * <p>El build corre fuera de Docker, así que la imagen solo copia el jar: no hace falta el token del
 * registro dentro del build de la imagen. El contexto es el directorio del jar, así que no hace falta
 * un {@code .dockerignore}.
 */
@UntrackedTask(because = "The image lives in the Docker daemon, outside the build")
public abstract class DockerBuildTask extends DefaultTask {

    /** Crea la tarea; la instancia Gradle. */
    public DockerBuildTask() {}

    /**
     * El jar ejecutable del servicio.
     *
     * @return la propiedad
     */
    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getJar();

    /**
     * La etiqueta de la imagen. Por defecto {@code <proyecto>:<versión>}.
     *
     * @return la propiedad
     */
    @Input
    @Option(option = "tag", description = "The image tag. <project>:<version> by default.")
    public abstract Property<String> getImage();

    /**
     * Dónde se escribe el Dockerfile de la plataforma antes de construir.
     *
     * @return la propiedad
     */
    @Internal
    public abstract RegularFileProperty getDockerfile();

    /**
     * Para ejecutar docker.
     *
     * @return las operaciones de proceso de Gradle
     */
    @Inject
    protected abstract ExecOperations getExec();

    /** Escribe el Dockerfile y construye la imagen. */
    @TaskAction
    public void build() {
        File dockerfile = getDockerfile().get().getAsFile();
        File jar = getJar().get().getAsFile();
        try {
            Files.createDirectories(dockerfile.toPath().getParent());
            Files.writeString(dockerfile.toPath(), NovaDockerfile.content(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("nova: no se pudo escribir " + dockerfile, e);
        }
        ExecResult result = getExec().exec(spec -> {
            spec.commandLine(
                    "docker",
                    "build",
                    "--file",
                    dockerfile.getAbsolutePath(),
                    "--build-arg",
                    "JAR_FILE=" + jar.getName(),
                    "--tag",
                    getImage().get(),
                    jar.getParentFile().getAbsolutePath());
            spec.setIgnoreExitValue(true);
        });
        if (result.getExitValue() != 0) {
            throw new GradleException("nova: docker build falló con el código " + result.getExitValue());
        }
        getLogger().lifecycle("nova: imagen {}", getImage().get());
    }
}
