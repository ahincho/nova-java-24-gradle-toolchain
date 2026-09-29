package pe.edu.nova.java.gradle.quality.commit;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * El hook {@code commit-msg}. Lo ejecuta git con un {@code java} a secas, sin Gradle, porque Gradle
 * tarda segundos en arrancar y un hook lento se termina saltando.
 *
 * <p>Sale con {@value #INVALID} cuando el mensaje no es válido. El script del hook distingue ese
 * código de cualquier otro fallo -un {@code java} viejo, un jar borrado- y en ese caso deja pasar el
 * commit, porque el CI lo valida igual.
 */
public final class CommitMessageHook {

    /** El código de salida de un mensaje inválido. */
    public static final int INVALID = 3;

    private static final String SCISSORS = "# ------------------------ >8 ------------------------";

    private CommitMessageHook() {}

    /**
     * Valida el archivo que git le pasa al hook.
     *
     * @param args la ruta del archivo con el mensaje
     */
    public static void main(String[] args) {
        // En UTF-8 siempre: con la codificación de la consola de Windows, las tildes salían rotas en la
        // terminal de Git, que lee UTF-8.
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        System.exit(run(args, err));
    }

    /**
     * Valida el archivo y dice qué está mal.
     *
     * @param args la ruta del archivo con el mensaje
     * @param err dónde escribir los problemas
     * @return 0 si el mensaje es válido, {@value #INVALID} si no, 2 si no se pudo leer
     */
    static int run(String[] args, PrintStream err) {
        if (args.length != 1) {
            err.println("nova: el hook commit-msg espera la ruta del mensaje");
            return 2;
        }
        String message;
        try {
            message = withoutComments(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8));
        } catch (IOException e) {
            err.println("nova: no se pudo leer el mensaje de commit: " + e.getMessage());
            return 2;
        }
        List<String> problems = ConventionalCommit.problems(message);
        if (problems.isEmpty()) {
            return 0;
        }
        err.println("nova: el mensaje de commit no sigue Conventional Commits (ADR-044):");
        problems.forEach(problem -> err.println("  - " + problem));
        err.println("  Por ejemplo: feat(orders): confirm a pending order");
        return INVALID;
    }

    /**
     * Quita lo que git agrega y no guarda: las líneas que empiezan con {@code #} y todo lo que sigue a
     * la línea de tijeras de {@code git commit -v}.
     *
     * @param raw el archivo tal como lo deja git
     * @return el mensaje que se va a guardar
     */
    static String withoutComments(String raw) {
        List<String> kept = new ArrayList<>();
        for (String line : raw.split("\\R", -1)) {
            if (line.equals(SCISSORS)) {
                break;
            }
            if (!line.startsWith("#")) {
                kept.add(line);
            }
        }
        return String.join("\n", kept);
    }
}
