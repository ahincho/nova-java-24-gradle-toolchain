package pe.edu.nova.java.gradle.springboot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** El modo nativo de ADR-045 sobre proyectos reales, con TestKit y el configuration cache encendido. */
class NovaNativeImageFunctionalTest {

    private static final String PLUGINS = """
            plugins {
                id("pe.edu.nova.java.spring-boot-service")
            }

            version = "1.0.0"

            // Se imprime al configurar: una tarea que lo hiciera no sería compatible con el configuration cache.
            println("NATIVE_BUILD_TOOLS=${pluginManager.hasPlugin("org.graalvm.buildtools.native")}")
            """;

    @TempDir
    Path directory;

    @BeforeEach
    void aProject() {
        write("settings.gradle.kts", "rootProject.name = \"sample\"\n");
    }

    @Test
    void withoutTheNativeModeTheServiceStaysOnTheJvm() {
        write("build.gradle.kts", PLUGINS);

        BuildResult result = run("tasks", "--group", "nova");

        assertThat(result.getOutput())
                .contains("NATIVE_BUILD_TOOLS=false")
                .contains("novaDocker")
                .doesNotContain("novaDockerNative");
    }

    @Test
    void theNativeModeAppliesNativeBuildToolsAndRegistersTheNativeImage() {
        write("gradle.properties", "nova.native=true\n");
        write("build.gradle.kts", PLUGINS);

        BuildResult result = run("tasks", "--group", "nova");

        assertThat(result.getOutput()).contains("NATIVE_BUILD_TOOLS=true").contains("novaDockerNative");
    }

    @Test
    void theCodeThatAotGeneratesIsNotCompiledWithWerror() {
        write("gradle.properties", "nova.native=true\n");
        write("build.gradle.kts", PLUGINS + """

                println("AOT_ARGS=${tasks.named<JavaCompile>("compileAotJava").get().options.compilerArgs}")
                println("MAIN_ARGS=${tasks.named<JavaCompile>("compileJava").get().options.compilerArgs}")
                """);

        String output = run("help").getOutput();

        assertThat(line(output, "AOT_ARGS=")).doesNotContain("-Werror");
        assertThat(line(output, "MAIN_ARGS=")).contains("-Werror");
    }

    /**
     * Construye la imagen nativa de un servicio real, la arranca y le hace una petición. Tarda minutos y
     * pide varios GB de RAM, así que corre solo con {@code NOVA_NATIVE_TEST=true}; además resuelve los
     * starters de Nova, así que pide un token.
     */
    @Test
    void aNativeServiceBuildsItsImageStartsAndAnswers() throws Exception {
        assumeTrue("true".equals(System.getenv("NOVA_NATIVE_TEST")), "NOVA_NATIVE_TEST=true runs the native build");
        assumeTrue(System.getenv("GITHUB_TOKEN") != null, "GITHUB_TOKEN is needed to read the Nova packages");
        assumeTrue(docker("info") == 0, "Docker is not available");
        write("gradle.properties", "nova.native=true\n");
        write("build.gradle.kts", PLUGINS + """

                dependencies {
                    implementation("org.springframework.boot:spring-boot-starter-webmvc")
                    implementation("org.springframework.boot:spring-boot-starter-actuator")
                }
                """);
        write("src/main/java/sample/SampleApplication.java", """
                package sample;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RestController;

                @SpringBootApplication
                @RestController
                public class SampleApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(SampleApplication.class, args);
                    }

                    public record Greeting(String message) {}

                    @GetMapping("/greetings")
                    public Greeting greet(@RequestParam String name) {
                        return new Greeting("Hola, " + name);
                    }
                }
                """);
        String image = "nova-toolchain-test/sample:" + System.nanoTime() + "-native";
        String container = null;
        try {
            run("novaDockerNative", "--tag=" + image);
            assertThat(dockerOutput("image", "inspect", "--format", "{{.Config.User}} {{.Config.ExposedPorts}}", image))
                    .contains("10001:10001")
                    .contains("8080/tcp");
            // Distroless sin shell, y con el SBOM del jar para que un escáner vea las librerías (ADR-046).
            assertThat(docker("run", "--rm", "--entrypoint", "sh", image, "-c", "true"))
                    .isNotZero();
            String created = dockerOutput("create", image).strip();
            try {
                assertThat(dockerOutput("cp", created + ":/application/sbom/application.cdx.json", "-"))
                        .contains("\"bomFormat\"");
            } finally {
                docker("rm", created);
            }

            container = dockerOutput("run", "--detach", "--publish", "127.0.0.1::8080", image)
                    .strip();
            String address = "http://"
                    + dockerOutput("port", container, "8080/tcp")
                            .lines()
                            .findFirst()
                            .orElseThrow();

            assertThat(awaitHealthy(address))
                    .as("el servicio nativo responde /actuator/health en 30 s")
                    .isTrue();
            HttpResponse<String> greeting = get(address + "/greetings?name=Angel");
            assertThat(greeting.statusCode()).as("status de /greetings, con el cuerpo %s", greeting.body())
                    .isEqualTo(200);
            assertThat(greeting.body()).contains("Hola, Angel");
        } catch (AssertionError e) {
            // El contenedor se borra en el finally: si el servicio falla, sus logs tienen que ir en el mensaje.
            if (container == null) {
                throw e;
            }
            throw new AssertionError(
                    e.getMessage() + System.lineSeparator() + "Logs del servicio:" + System.lineSeparator()
                            + logsOf(container),
                    e);
        } finally {
            if (container != null) {
                docker("rm", "--force", container);
            }
            docker("rmi", "--force", image);
        }
    }

    private static boolean awaitHealthy(String address) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (get(address + "/actuator/health").statusCode() == 200) {
                    return true;
                }
            } catch (IOException e) {
                // Todavía no escucha: se vuelve a intentar.
            }
            Thread.sleep(100);
        }
        return false;
    }

    private static String logsOf(String container) {
        try {
            return dockerOutput("logs", container);
        } catch (IOException e) {
            return "no se pudieron leer: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "no se pudieron leer: se interrumpió la lectura";
        }
    }

    private static HttpResponse<String> get(String url) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(5))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private static String line(String output, String prefix) {
        return output.lines()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }

    private BuildResult run(String... arguments) {
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.remove("CI");
        environment.remove("GITHUB_ACTIONS");
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

    private static int docker(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("docker"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }

    private static String dockerOutput(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("docker"));
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
}
