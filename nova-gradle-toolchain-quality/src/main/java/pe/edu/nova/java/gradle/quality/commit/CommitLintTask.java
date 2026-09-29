package pe.edu.nova.java.gradle.quality.commit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

/**
 * Valida los mensajes de commit de un rango, como hace el CI con los commits de un PR, o el de un
 * archivo, como hace el hook.
 *
 * <pre>
 * ./gradlew novaCommitLint --from=origin/main
 * ./gradlew novaCommitLint --from=&lt;base&gt; --to=&lt;head&gt;
 * ./gradlew novaCommitLint --message-file=.git/COMMIT_EDITMSG
 * </pre>
 */
@UntrackedTask(because = "It reads the git history, which Gradle cannot track as an input")
public abstract class CommitLintTask extends DefaultTask {

    private static final char FIELD = '\u001f';
    private static final char RECORD = '\u001e';

    /** Crea la tarea; la instancia Gradle. */
    public CommitLintTask() {}

    /**
     * El commit desde el que se valida, sin incluirlo: la base del PR.
     *
     * @return la propiedad
     */
    @Input
    @Optional
    @Option(option = "from", description = "The commit to validate from, excluded: the base of the pull request.")
    public abstract Property<String> getFrom();

    /**
     * El último commit que se valida. Por defecto {@code HEAD}.
     *
     * @return la propiedad
     */
    @Input
    @Optional
    @Option(option = "to", description = "The last commit to validate. HEAD by default.")
    public abstract Property<String> getTo();

    /**
     * Un archivo con un solo mensaje, en lugar de un rango.
     *
     * @return la propiedad
     */
    @Input
    @Optional
    @Option(option = "message-file", description = "A file with a single commit message, instead of a range.")
    public abstract Property<String> getMessageFile();

    /**
     * El directorio desde el que se llama a git.
     *
     * @return la propiedad
     */
    @Internal
    public abstract DirectoryProperty getWorkingDirectory();

    /**
     * Para ejecutar git.
     *
     * @return las operaciones de proceso de Gradle
     */
    @Inject
    protected abstract ExecOperations getExec();

    /** Valida y falla con la lista de problemas. */
    @TaskAction
    public void lint() {
        List<Commit> commits = commits();
        List<String> report = new ArrayList<>();
        int checked = 0;
        for (Commit commit : commits) {
            if (ConventionalCommit.isIgnored(commit.message())) {
                continue;
            }
            checked++;
            List<String> problems = ConventionalCommit.problems(commit.message());
            if (!problems.isEmpty()) {
                report.add(commit.label());
                problems.forEach(problem -> report.add("    - " + problem));
            }
        }
        if (!report.isEmpty()) {
            throw new GradleException("nova: hay mensajes de commit que no siguen Conventional Commits (ADR-044):\n  "
                    + String.join("\n  ", report));
        }
        getLogger().lifecycle("nova: {} mensajes de commit revisados, todos siguen Conventional Commits", checked);
    }

    private List<Commit> commits() {
        if (getMessageFile().isPresent()) {
            Path file = getWorkingDirectory()
                    .get()
                    .getAsFile()
                    .toPath()
                    .resolve(getMessageFile().get());
            try {
                String message = CommitMessageHook.withoutComments(Files.readString(file, StandardCharsets.UTF_8));
                return List.of(new Commit(file.getFileName().toString(), message));
            } catch (IOException e) {
                throw new UncheckedIOException("nova: no se pudo leer " + file, e);
            }
        }
        if (!getFrom().isPresent()) {
            throw new GradleException("nova: novaCommitLint necesita --from=<commit> o --message-file=<ruta>");
        }
        String range = getFrom().get() + ".." + getTo().getOrElse("HEAD");
        String log = git("log", "--no-merges", "--format=%H" + FIELD + "%B" + RECORD, range);
        List<Commit> commits = new ArrayList<>();
        for (String record : log.split(String.valueOf(RECORD))) {
            String trimmed = record.strip();
            int separator = trimmed.indexOf(FIELD);
            if (separator > 0) {
                commits.add(new Commit(trimmed.substring(0, 7), trimmed.substring(separator + 1)));
            }
        }
        return commits;
    }

    private String git(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        ExecResult result = getExec().exec(spec -> {
            spec.commandLine(command);
            spec.workingDir(getWorkingDirectory().get().getAsFile());
            spec.setStandardOutput(out);
            spec.setErrorOutput(err);
            spec.setIgnoreExitValue(true);
        });
        if (result.getExitValue() != 0) {
            throw new GradleException("nova: git " + String.join(" ", args) + " falló: "
                    + err.toString(StandardCharsets.UTF_8).strip());
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private record Commit(String id, String message) {

        String label() {
            String header = message.strip().lines().findFirst().orElse("");
            return id + " " + header;
        }
    }
}
