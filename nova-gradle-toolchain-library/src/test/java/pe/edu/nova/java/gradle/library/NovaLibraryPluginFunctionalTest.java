package pe.edu.nova.java.gradle.library;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** El plugin de librerías sobre proyectos reales, con TestKit y el configuration cache encendido. */
class NovaLibraryPluginFunctionalTest {

    private static final String PLUGINS = """
            plugins {
                id("pe.edu.nova.java.library")
            }

            group = "pe.edu.nova.java.libs"
            version = "1.0.0"
            description = "A sample library."

            // Se imprime al configurar: una tarea que lo hiciera no sería compatible con el configuration cache.
            println("PLUGINS=${pluginManager.hasPlugin("java-library")} "
                + "${pluginManager.hasPlugin("pe.edu.nova.java.quality")} "
                + "${pluginManager.hasPlugin("maven-publish")}")
            println("REPOSITORIES=${publishing.repositories.names}")
            """;

    @TempDir
    Path directory;

    private final Map<String, String> environment = new HashMap<>(System.getenv());

    @BeforeEach
    void aLocalBuild() {
        // Las pruebas corren en CI, pero el proyecto de prueba tiene que comportarse como un build local.
        environment.remove("CI");
        environment.remove("GITHUB_ACTIONS");
        environment.remove("GITHUB_REPOSITORY");
        environment.remove("GPG_SIGNING_KEY_ID");
        environment.remove("GPG_SIGNING_KEY");
        write("settings.gradle.kts", "rootProject.name = \"sample\"\n");
    }

    @Test
    void itAppliesTheQualityPluginJavaLibraryAndPublishing() {
        write("build.gradle.kts", PLUGINS);

        BuildResult result = run("tasks", "--group", "nova");

        assertThat(result.getOutput())
                .contains("PLUGINS=true true true")
                .contains("novaVerify")
                .contains("novaSecurity");
    }

    @Test
    void withoutARepositoryItDoesNotPublishToGitHubPackages() {
        write("build.gradle.kts", PLUGINS);

        BuildResult result = run("help");

        assertThat(result.getOutput()).contains("REPOSITORIES=[]");
    }

    @Test
    void theRepositoryOfTheCiPublishesToItsGitHubPackages() {
        write("build.gradle.kts", PLUGINS);
        environment.put("GITHUB_REPOSITORY", "ahincho/nova-java-99-sample");

        BuildResult result = run("help");

        assertThat(result.getOutput()).contains("REPOSITORIES=[GitHubPackages]");
    }

    @Test
    void itPublishesTheJarsWithTheNovaPom() {
        write("gradle.properties", "nova.repository=ahincho/nova-java-99-sample\n");
        write("build.gradle.kts", PLUGINS + """

                publishing {
                    repositories {
                        maven {
                            name = "Local"
                            url = uri(layout.buildDirectory.dir("repository"))
                        }
                    }
                }
                """);
        write("src/main/java/sample/Greeter.java", """
                package sample;

                /** Saluda. */
                public final class Greeter {

                    /** Crea el saludador. */
                    public Greeter() {}

                    /**
                     * Saluda a alguien.
                     *
                     * @param name a quién
                     * @return el saludo
                     */
                    public String greet(String name) {
                        return "Hola, " + name;
                    }
                }
                """);

        run("publishMavenJavaPublicationToLocalRepository");

        Path published = directory.resolve("build/repository/pe/edu/nova/java/libs/sample/1.0.0");
        assertThat(published.resolve("sample-1.0.0.jar")).exists();
        assertThat(published.resolve("sample-1.0.0-sources.jar")).exists();
        assertThat(published.resolve("sample-1.0.0-javadoc.jar")).exists();
        assertThat(read(published.resolve("sample-1.0.0.pom")))
                .contains("<description>A sample library.</description>")
                .contains("<url>https://github.com/ahincho/nova-java-99-sample</url>")
                .contains("<name>Eclipse Public License 2.0</name>")
                .contains("<connection>scm:git:https://github.com/ahincho/nova-java-99-sample.git</connection>");
    }

    private BuildResult run(String... arguments) {
        List<String> all = new ArrayList<>(List.of(arguments));
        all.add("--configuration-cache");
        all.add("--stacktrace");
        return GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withPluginClasspath()
                .withEnvironment(environment)
                .withArguments(all)
                .forwardOutput()
                .build();
    }

    private void write(String path, String content) {
        Path file = directory.resolve(path);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
