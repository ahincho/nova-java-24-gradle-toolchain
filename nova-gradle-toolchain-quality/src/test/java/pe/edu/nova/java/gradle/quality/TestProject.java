package pe.edu.nova.java.gradle.quality;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

/** Un proyecto de prueba en un directorio temporal, con el plugin aplicado y git a mano. */
final class TestProject {

    private final Path directory;
    private final Map<String, String> environment;

    TestProject(Path directory, String buildScript) {
        this.directory = directory;
        this.environment = new HashMap<>(System.getenv());
        // Las pruebas corren en CI, pero el proyecto de prueba tiene que comportarse como un build local.
        environment.remove("CI");
        environment.remove("GITHUB_ACTIONS");
        environment.put("JAVA_HOME", System.getProperty("java.home"));
        write("settings.gradle.kts", "rootProject.name = \"sample\"\n");
        write("build.gradle.kts", buildScript);
    }

    /** Un proyecto con el plugin y nada más. */
    static TestProject quality(Path directory) {
        return new TestProject(directory, "plugins {\n    id(\"pe.edu.nova.java.quality\")\n}\n");
    }

    TestProject inCi() {
        environment.put("CI", "true");
        return this;
    }

    TestProject write(String path, String content) {
        Path file = directory.resolve(path);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    String read(String path) {
        try {
            return Files.readString(directory.resolve(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    boolean exists(String path) {
        return Files.exists(directory.resolve(path));
    }

    BuildResult build(String... arguments) {
        return runner(arguments).build();
    }

    BuildResult fail(String... arguments) {
        return runner(arguments).buildAndFail();
    }

    /** Ejecuta git en el proyecto y devuelve el código de salida. */
    int git(String... arguments) {
        List<String> command = new ArrayList<>(List.of(
                "git", "-c", "user.name=Nova Test", "-c", "user.email=nova@example.com", "-c", "commit.gpgsign=false"));
        command.addAll(List.of(arguments));
        ProcessBuilder builder =
                new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().putAll(environment);
        try {
            Process process = builder.start();
            process.getInputStream().readAllBytes();
            return process.waitFor();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Ejecuta git y devuelve lo que imprime. */
    String gitOutput(String... arguments) {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            process.waitFor();
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private GradleRunner runner(String... arguments) {
        List<String> all = new ArrayList<>(List.of(arguments));
        all.add("--configuration-cache");
        all.add("--stacktrace");
        return GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withPluginClasspath()
                .withEnvironment(environment)
                .withArguments(all)
                .forwardOutput();
    }
}
