package pe.edu.nova.java.gradle.springboot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** El plugin de Spring Boot sobre proyectos reales, con TestKit y el configuration cache encendido. */
class NovaSpringBootPluginFunctionalTest {

    private static final String PLUGINS = """
            plugins {
                id("pe.edu.nova.java.spring-boot-service")
            }
            """;

    @TempDir
    Path directory;

    @Test
    void itAppliesSpringBootTheNovaStartersAndTheTasks() {
        write("build.gradle.kts", PLUGINS + """

                // Se imprime al configurar: una tarea que lo hiciera no sería compatible con el configuration cache.
                val declared = configurations.getByName("implementation").dependencies.map { "${it.group}:${it.name}" }
                println("SPRING_BOOT=${pluginManager.hasPlugin("org.springframework.boot")} "
                    + "QUALITY=${pluginManager.hasPlugin("pe.edu.nova.java.quality")}")
                println("DEPENDENCIES=$declared")
                """);

        BuildResult result = run("tasks", "--group", "nova");

        assertThat(result.getOutput())
                .contains("SPRING_BOOT=true QUALITY=true")
                .contains("org.springframework.boot:spring-boot-dependencies")
                .contains("pe.edu.nova.java.starters:nova-mask-spring-boot-starter")
                .contains("pe.edu.nova.java.starters:nova-api-standard-spring-boot-starter")
                .contains("novaVerify")
                .contains("novaSecurity")
                .contains("novaDocker")
                .contains("novaDockerEject");
    }

    @Test
    void itBringsTheTestStartersThatAMockMvcTestNeeds() {
        write("build.gradle.kts", PLUGINS + """

                // Se imprime al configurar: una tarea que lo hiciera no sería compatible con el configuration cache.
                val declared = configurations.getByName("testImplementation").dependencies.map { "${it.group}:${it.name}" }
                println("TEST_DEPENDENCIES=$declared")
                """);

        BuildResult result = run("help");

        assertThat(result.getOutput())
                .contains("org.springframework.boot:spring-boot-starter-test")
                .contains("org.springframework.boot:spring-boot-starter-webmvc-test")
                .contains("pe.edu.nova.java.libs:nova-architecture-rules");
    }

    @Test
    void itPinsTheSecurityPatchesThatSpringBootDoesNotBringYet() {
        write("build.gradle.kts", PLUGINS + """

                // Se imprime al configurar: una tarea que lo hiciera no sería compatible con el configuration cache.
                val implementation = configurations.getByName("implementation")
                println("CONSTRAINTS=${implementation.dependencyConstraints.map { "${it.group}:${it.name}:${it.version}" }}")
                println("PLATFORMS=${implementation.dependencies.map { "${it.group}:${it.name}:${it.version}" }}")
                """);

        BuildResult result = run("help");

        assertThat(result.getOutput())
                .contains("org.apache.tomcat.embed:tomcat-embed-core:11.0.26")
                .contains("org.apache.tomcat.embed:tomcat-embed-el:11.0.26")
                .contains("org.apache.tomcat.embed:tomcat-embed-websocket:11.0.26")
                .contains("tools.jackson:jackson-bom:3.1.7");
    }

    @Test
    void novaDockerEjectWritesTheDockerfileWithItsOrigin() {
        write("build.gradle.kts", PLUGINS);

        run("novaDockerEject");

        List<String> lines = read("Dockerfile").lines().toList();
        assertThat(lines.get(0)).isEqualTo("# syntax=docker/dockerfile:1.7");
        assertThat(lines.get(1)).startsWith("# Escrito por novaDockerEject");
        assertThat(read("Dockerfile")).contains(NovaDockerfile.MARKER).contains("USER 10001:10001");
    }

    @Test
    void novaDockerEjectNeverReplacesADockerfileThatIsNotFromNova() {
        write("build.gradle.kts", PLUGINS);
        write("Dockerfile", "FROM scratch\n");

        BuildResult result = runner("novaDockerEject").buildAndFail();

        assertThat(result.getOutput()).contains("no es de Nova");
        assertThat(read("Dockerfile")).isEqualTo("FROM scratch\n");
    }

    /**
     * Un servicio de verdad: resuelve los starters de Nova desde GitHub Packages, así que necesita un
     * token, y si hay Docker construye la imagen.
     */
    @Test
    void aServiceVerifiesBuildsItsJarAndItsImage() throws Exception {
        assumeTrue(System.getenv("GITHUB_TOKEN") != null, "GITHUB_TOKEN is needed to read the Nova packages");
        write("build.gradle.kts", PLUGINS + """

                version = "1.0.0"

                dependencies {
                    implementation("org.springframework.boot:spring-boot-starter")
                }
                """);
        writeSampleApplication();
        write("src/main/java/sample/Greeter.java", """
                package sample;

                public final class Greeter {
                    public String greet(String name) {
                        return "Hola, " + name;
                    }
                }
                """);
        write("src/test/java/sample/GreeterTest.java", """
                package sample;

                import static org.assertj.core.api.Assertions.assertThat;

                import org.junit.jupiter.api.Test;

                class GreeterTest {
                    @Test
                    void greetsByName() {
                        assertThat(new Greeter().greet("Angel")).isEqualTo("Hola, Angel");
                    }
                }
                """);

        BuildResult result = run("novaVerify", "bootJar");

        assertThat(result.task(":jacocoTestCoverageVerification").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(directory.resolve("build/libs/sample-1.0.0.jar")).exists();

        assumeTrue(docker("info") == 0, "Docker is not available");
        String image = "nova-toolchain-test/sample:" + System.nanoTime();
        try {
            run("novaDocker", "--tag=" + image, "--build-arg=JAVA_VERSION=25");
            assertThat(docker("image", "inspect", image)).isZero();
            assertThat(dockerOutput("image", "inspect", "--format", "{{.Config.User}} {{.Config.ExposedPorts}}", image))
                    .contains("10001:10001")
                    .contains("8080/tcp");
            // La imagen final es distroless (ADR-046): arranca la aplicación y no trae shell.
            assertThat(dockerOutput("run", "--rm", image)).contains("Started SampleApplication");
            assertThat(docker("run", "--rm", "--entrypoint", "sh", image, "-c", "true"))
                    .isNotZero();
        } finally {
            docker("rmi", "--force", image);
        }
    }

    /**
     * Un servicio con una prueba web, que no declara ningún starter de pruebas: {@code @WebMvcTest} y MockMvc salen
     * de {@code spring-boot-starter-webmvc-test}, que trae el plugin. Resuelve los starters de Nova desde GitHub
     * Packages, así que necesita un token.
     */
    @Test
    void aServiceRunsAMockMvcTestWithoutDeclaringTheStarter() {
        assumeTrue(System.getenv("GITHUB_TOKEN") != null, "GITHUB_TOKEN is needed to read the Nova packages");
        write("build.gradle.kts", PLUGINS + """

                dependencies {
                    implementation("org.springframework.boot:spring-boot-starter")
                }
                """);
        writeSampleApplication();
        write("src/test/java/sample/SampleWebTest.java", """
                package sample;

                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
                import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

                import org.junit.jupiter.api.Test;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
                import org.springframework.test.web.servlet.MockMvc;

                @WebMvcTest
                class SampleWebTest {
                    @Autowired
                    private MockMvc mvc;

                    @Test
                    void anUnknownPathIsNotFound() throws Exception {
                        mvc.perform(get("/nowhere")).andExpect(status().isNotFound());
                    }
                }
                """);

        BuildResult result = run("test");

        assertThat(result.task(":test").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        // La prueba corrió de verdad: sin el starter ni siquiera compila.
        assertThat(read("build/test-results/test/TEST-sample.SampleWebTest.xml"))
                .contains("tests=\"1\"");
    }

    private void writeSampleApplication() {
        write("src/main/java/sample/SampleApplication.java", """
                package sample;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class SampleApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(SampleApplication.class, args);
                    }
                }
                """);
    }

    private BuildResult run(String... arguments) {
        return runner(arguments).build();
    }

    private GradleRunner runner(String... arguments) {
        write("settings.gradle.kts", "rootProject.name = \"sample\"\n");
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.remove("CI");
        environment.remove("GITHUB_ACTIONS");
        List<String> all = new java.util.ArrayList<>(List.of(arguments));
        all.add("--configuration-cache");
        all.add("--stacktrace");
        return GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withPluginClasspath()
                .withEnvironment(environment)
                .withArguments(all)
                .forwardOutput();
    }

    private static int docker(String... arguments) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>(List.of("docker"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }

    private static String dockerOutput(String... arguments) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>(List.of("docker"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return output;
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

    private String read(String path) {
        try {
            return Files.readString(directory.resolve(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
