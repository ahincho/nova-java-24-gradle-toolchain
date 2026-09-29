package pe.edu.nova.java.gradle.quality.commit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

/**
 * Instala el hook {@code commit-msg} que valida cada mensaje al hacer el commit.
 *
 * <p>El hook no llama a Gradle: ejecuta con {@code java} una copia del jar del toolchain que queda en
 * el directorio de hooks. Si ya hay un {@code commit-msg} que no es de Nova, como el de lefthook, no
 * lo reemplaza. Se puede borrar sin romper nada, porque el CI valida los mismos mensajes.
 */
@UntrackedTask(because = "It writes into the git directory, outside the build")
public abstract class InstallGitHooksTask extends DefaultTask {

    /** La marca que identifica un hook instalado por Nova. */
    static final String MARKER = "nova-toolchain";

    private static final String JAR = "nova-commit-msg.jar";

    /** Crea la tarea; la instancia Gradle. */
    public InstallGitHooksTask() {}

    /**
     * El directorio desde el que se pregunta a git dónde van los hooks.
     *
     * @return la propiedad
     */
    @Internal
    public abstract DirectoryProperty getWorkingDirectory();

    /**
     * El jar o el directorio de clases del toolchain, que el hook pone en su classpath.
     *
     * @return la propiedad
     */
    @Internal
    public abstract Property<String> getToolchainLocation();

    /**
     * Para ejecutar git.
     *
     * @return las operaciones de proceso de Gradle
     */
    @Inject
    protected abstract ExecOperations getExec();

    /** Instala el hook, o no hace nada si ya está al día. */
    @TaskAction
    public void install() {
        Path workingDirectory = getWorkingDirectory().get().getAsFile().toPath();
        String hooksPath = git(workingDirectory, "rev-parse", "--git-path", "hooks");
        if (hooksPath == null) {
            getLogger().info("nova: no es un repositorio git, así que no se instala el hook commit-msg");
            return;
        }
        Path hooks = workingDirectory.resolve(hooksPath).normalize();
        Path hook = hooks.resolve("commit-msg");
        try {
            if (Files.exists(hook)
                    && !Files.readString(hook, StandardCharsets.UTF_8).contains(MARKER)) {
                getLogger()
                        .warn(
                                "nova: ya hay un hook commit-msg que no es de Nova en {}; no se reemplaza. "
                                        + "Los mensajes se validan igual en el CI.",
                                hook);
                return;
            }
            Files.createDirectories(hooks);
            String classpath = classpath(hooks);
            writeIfChanged(hook, script(classpath));
            if (!hook.toFile().setExecutable(true)) {
                getLogger().warn("nova: no se pudo marcar {} como ejecutable", hook);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("nova: no se pudo instalar el hook commit-msg en " + hooks, e);
        }
    }

    private String classpath(Path hooks) throws IOException {
        Path toolchain = Path.of(getToolchainLocation().get());
        if (Files.isDirectory(toolchain)) {
            // Solo pasa al probar el plugin desde sus clases, sin empaquetar.
            return toolchain.toAbsolutePath().toString().replace('\\', '/');
        }
        Path copy = hooks.resolve(JAR);
        if (!Files.exists(copy) || Files.mismatch(copy, toolchain) != -1) {
            Files.copy(toolchain, copy, StandardCopyOption.REPLACE_EXISTING);
        }
        // El jar va copiado al lado del hook: el caché de Gradle se limpia y un hook que apunta ahí se rompe.
        return "$(dirname \"$0\")/" + JAR;
    }

    static String script(String classpath) {
        return """
                #!/bin/sh
                # %s: valida el mensaje con Conventional Commits (ADR-044). Lo instala novaInstallGitHooks.
                # Se puede borrar sin romper nada: el CI valida los mismos mensajes.
                JAVA="java"
                if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
                  JAVA="$JAVA_HOME/bin/java"
                elif ! command -v java > /dev/null 2>&1; then
                  echo "nova: java no está en el PATH; el mensaje se valida en el CI." >&2
                  exit 0
                fi
                "$JAVA" -cp "%s" pe.edu.nova.java.gradle.quality.commit.CommitMessageHook "$1"
                status=$?
                if [ "$status" -eq %d ]; then
                  exit 1
                fi
                if [ "$status" -ne 0 ]; then
                  echo "nova: no se pudo validar el mensaje (código $status); el CI lo valida igual." >&2
                fi
                exit 0
                """.formatted(MARKER, classpath, CommitMessageHook.INVALID);
    }

    private static void writeIfChanged(Path file, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (!Files.exists(file) || !Arrays.equals(Files.readAllBytes(file), bytes)) {
            Files.write(file, bytes);
        }
    }

    private String git(Path workingDirectory, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        ExecResult result = getExec().exec(spec -> {
            spec.commandLine((Object[]) command);
            spec.workingDir(workingDirectory.toFile());
            spec.setStandardOutput(out);
            spec.setErrorOutput(new ByteArrayOutputStream());
            spec.setIgnoreExitValue(true);
        });
        return result.getExitValue() == 0 ? out.toString(StandardCharsets.UTF_8).strip() : null;
    }
}
