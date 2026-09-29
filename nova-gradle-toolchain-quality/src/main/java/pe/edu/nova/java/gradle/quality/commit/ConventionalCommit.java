package pe.edu.nova.java.gradle.quality.commit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Las reglas de Conventional Commits que valida el toolchain (ADR-044).
 *
 * <p>Son las de {@code @commitlint/config-conventional}, que es lo que usaban los repositorios Java
 * antes del toolchain, más una: entre el encabezado y el cuerpo va una línea en blanco. No depende de
 * Gradle, porque también la ejecuta el hook {@code commit-msg} con un {@code java} a secas.
 */
public final class ConventionalCommit {

    /** Los tipos válidos. */
    public static final Set<String> TYPES = Set.copyOf(new TreeSet<>(
            List.of("build", "chore", "ci", "docs", "feat", "fix", "perf", "refactor", "revert", "style", "test")));

    /** El largo máximo del encabezado y de cada línea del cuerpo. */
    public static final int MAX_LINE_LENGTH = 100;

    private static final Pattern HEADER =
            Pattern.compile("^(?<type>[^\\s(!:]+)(?:\\((?<scope>[^()]*)\\))?(?<breaking>!)?:(?<subject>(?: .*)?)$");

    // Los mensajes que escribe git o GitHub, no una persona. Son los mismos que ignora commitlint.
    private static final List<Pattern> IGNORED = List.of(
            Pattern.compile("^Merge pull request #\\d+ from .*"),
            Pattern.compile("^Merge (remote-tracking )?branch .*"),
            Pattern.compile("^Merge tag .*"),
            Pattern.compile("^Merged .* (in|into) .*"),
            Pattern.compile("^Automatic merge.*"),
            Pattern.compile("^Auto-merged .* into .*"),
            Pattern.compile("^Revert \".*\""),
            Pattern.compile("^(fixup|squash|amend)! .*"),
            Pattern.compile("^Initial commit$"));

    private ConventionalCommit() {}

    /**
     * Si el mensaje lo generó git o GitHub y no se valida.
     *
     * @param message el mensaje completo
     * @return si se salta
     */
    public static boolean isIgnored(String message) {
        String header = header(message);
        return IGNORED.stream().anyMatch(pattern -> pattern.matcher(header).matches());
    }

    /**
     * Valida un mensaje de commit.
     *
     * @param message el mensaje completo, sin los comentarios que agrega git
     * @return los problemas encontrados, en el orden en que aparecen; vacío si el mensaje es válido
     */
    public static List<String> problems(String message) {
        List<String> problems = new ArrayList<>();
        if (message == null || message.isBlank()) {
            problems.add("el mensaje está vacío");
            return problems;
        }
        if (isIgnored(message)) {
            return problems;
        }
        String[] lines = message.strip().split("\\R", -1);
        checkHeader(lines[0], problems);
        if (lines.length > 1 && !lines[1].isBlank()) {
            problems.add("falta una línea en blanco entre el encabezado y el cuerpo");
        }
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].length() > MAX_LINE_LENGTH) {
                problems.add("la línea " + (i + 1) + " pasa de " + MAX_LINE_LENGTH + " caracteres");
            }
        }
        return problems;
    }

    private static void checkHeader(String header, List<String> problems) {
        if (header.length() > MAX_LINE_LENGTH) {
            problems.add("el encabezado pasa de " + MAX_LINE_LENGTH + " caracteres");
        }
        Matcher matcher = HEADER.matcher(header);
        if (!matcher.matches()) {
            problems.add("el encabezado no sigue la forma <tipo>(<alcance>): <descripción>");
            return;
        }
        String type = matcher.group("type");
        if (!type.equals(type.toLowerCase(Locale.ROOT))) {
            problems.add("el tipo tiene que ir en minúscula: " + type);
        } else if (!TYPES.contains(type)) {
            problems.add(
                    "el tipo '" + type + "' no existe; los válidos son " + String.join(", ", new TreeSet<>(TYPES)));
        }
        String scope = matcher.group("scope");
        if (scope != null) {
            if (scope.isBlank()) {
                problems.add("el alcance entre paréntesis no puede ir vacío");
            } else if (!scope.equals(scope.toLowerCase(Locale.ROOT))) {
                problems.add("el alcance tiene que ir en minúscula: " + scope);
            }
        }
        String subject = matcher.group("subject").strip();
        if (subject.isEmpty()) {
            problems.add("falta la descripción después de los dos puntos");
            return;
        }
        if (Character.isUpperCase(subject.codePointAt(0))) {
            problems.add("la descripción tiene que empezar en minúscula");
        }
        if (subject.endsWith(".")) {
            problems.add("la descripción no puede terminar en punto");
        }
    }

    private static String header(String message) {
        String stripped = message.strip();
        int end = stripped.indexOf('\n');
        return (end < 0 ? stripped : stripped.substring(0, end)).strip();
    }
}
