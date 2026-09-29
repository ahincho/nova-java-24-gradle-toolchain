package pe.edu.nova.java.gradle.quality;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.spotless.LineEnding;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import pe.edu.nova.java.gradle.quality.commit.CommitLintTask;
import pe.edu.nova.java.gradle.quality.commit.InstallGitHooksTask;

/**
 * {@code pe.edu.nova.java.quality}: lo que el toolchain de Java aplica a todo proyecto (ADR-044).
 *
 * <ul>
 *   <li>Java 25, con {@code -Xlint:all -Werror} y {@code -parameters};</li>
 *   <li>Spotless con palantir-java-format, que formatea y lo corrige solo;</li>
 *   <li>Checkstyle solo para defectos, en error y con {@code maxWarnings = 0};</li>
 *   <li>JUnit Platform, AssertJ y las versiones de Testcontainers y ArchUnit;</li>
 *   <li>JaCoCo con un mínimo de líneas que cuelga de {@code check};</li>
 *   <li>la validación de los mensajes de commit y su hook;</li>
 *   <li>los paquetes de Nova en GitHub Packages.</li>
 * </ul>
 *
 * <p>Las tareas {@code novaFormat} y {@code novaVerify} son el contrato: un repositorio no nombra
 * ninguna herramienta, así que cambiar una se hace aquí y en ningún otro lado.
 */
public class NovaQualityPlugin implements Plugin<Project> {

    /** El grupo de las tareas de Nova. */
    public static final String GROUP = "nova";

    /** El registro de los paquetes de Nova. GitHub resuelve desde él cualquier paquete de la cuenta. */
    public static final String NOVA_PACKAGES = "https://maven.pkg.github.com/ahincho/nova-java-24-gradle-toolchain";

    private static final int JAVA_VERSION = 25;
    private static final String CHECKSTYLE_CONFIG = "checkstyle.xml";

    /** Crea el plugin; lo instancia Gradle. */
    public NovaQualityPlugin() {}

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(CheckstylePlugin.class);
        project.getPluginManager().apply(JacocoPlugin.class);
        project.getPluginManager().apply("com.diffplug.spotless");

        NovaVersions versions = NovaVersions.load();
        NovaExtension nova = project.getExtensions().create(NovaExtension.NAME, NovaExtension.class);
        nova.getCoverageMinimum().convention(new BigDecimal("0.80"));
        nova.getCoverageExcludes().convention(List.of("**/*Application.class", "**/generated/**"));
        nova.getGitHooks().convention(true);

        repositories(project);
        compilation(project);
        formatting(project, versions);
        checkstyle(project, versions);
        tests(project, versions);
        coverage(project, versions, nova);
        project.getTasks().withType(AbstractArchiveTask.class).configureEach(archive -> {
            archive.setPreserveFileTimestamps(false);
            archive.setReproducibleFileOrder(true);
        });
        contract(project, nova);
    }

    private static void repositories(Project project) {
        project.getRepositories().mavenCentral();
        project.getRepositories().maven(repository -> {
            repository.setName("NovaPackages");
            repository.setUrl(NOVA_PACKAGES);
            // GitHub Packages pide credenciales incluso para leer: en CI el token del workflow, en local
            // un GITHUB_TOKEN con read:packages.
            repository.credentials(credentials -> {
                credentials.setUsername(env(project, "GITHUB_ACTOR"));
                String token = env(project, "NOVA_PACKAGES_READ_TOKEN");
                credentials.setPassword(token != null ? token : env(project, "GITHUB_TOKEN"));
            });
            repository.mavenContent(content -> content.includeGroupByRegex("pe\\.edu\\.nova(\\..*)?"));
        });
    }

    private static void compilation(Project project) {
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().convention(JavaLanguageVersion.of(JAVA_VERSION));
        project.getTasks().withType(JavaCompile.class).configureEach(compile -> {
            compile.getOptions().setEncoding("UTF-8");
            compile.getOptions().getRelease().convention(JAVA_VERSION);
            // -processing se apaga porque avisa de anotaciones que ningún procesador reclama, algo
            // normal con Spring o JPA, y con -Werror rompería el build sin que haya un defecto.
            compile.getOptions().getCompilerArgs().addAll(List.of("-parameters", "-Xlint:all,-processing", "-Werror"));
        });
    }

    private static void formatting(Project project, NovaVersions versions) {
        SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
        // LF siempre, como el .gitattributes de los repositorios de Nova. Sin esto, en Windows y sin
        // .gitattributes, Spotless exige CRLF y el mismo archivo pasa en CI y falla en local.
        spotless.setLineEndings(LineEnding.UNIX);
        spotless.java(java -> {
            java.target("src/*/java/**/*.java");
            java.palantirJavaFormat(versions.of("palantir.java.format"));
            java.importOrder("\\#", "");
            java.removeUnusedImports();
            java.trimTrailingWhitespace();
            java.endWithNewline();
        });
        spotless.kotlinGradle(kotlin -> {
            kotlin.target("*.gradle.kts");
            try {
                kotlin.ktlint(versions.of("ktlint"));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not configure ktlint for the Gradle scripts", e);
            }
        });
    }

    private static void checkstyle(Project project, NovaVersions versions) {
        CheckstyleExtension checkstyle = project.getExtensions().getByType(CheckstyleExtension.class);
        checkstyle.setToolVersion(versions.of("checkstyle"));
        checkstyle.setConfig(project.getResources().getText().fromString(resource(CHECKSTYLE_CONFIG)));
        checkstyle.setMaxWarnings(0);
        checkstyle.setIgnoreFailures(false);
    }

    private static void tests(Project project, NovaVersions versions) {
        DependencyHandler dependencies = project.getDependencies();
        String testImplementation = JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME;
        dependencies.add(testImplementation, dependencies.platform("org.junit:junit-bom:" + versions.of("junit")));
        dependencies.add(
                testImplementation,
                dependencies.platform("org.testcontainers:testcontainers-bom:" + versions.of("testcontainers")));
        dependencies.add(testImplementation, "org.junit.jupiter:junit-jupiter");
        dependencies.add(testImplementation, "org.assertj:assertj-core:" + versions.of("assertj"));
        dependencies.add(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME, "org.junit.platform:junit-platform-launcher");
        dependencies
                .getConstraints()
                .add(testImplementation, "com.tngtech.archunit:archunit-junit5:" + versions.of("archunit"));
        dependencies
                .getConstraints()
                .add(
                        testImplementation,
                        "pe.edu.nova.java.libs:nova-architecture-rules:" + versions.of("nova.architecture.rules"));
        project.getTasks().withType(Test.class).configureEach(Test::useJUnitPlatform);
    }

    private static void coverage(Project project, NovaVersions versions, NovaExtension nova) {
        project.getExtensions().getByType(JacocoPluginExtension.class).setToolVersion(versions.of("jacoco"));
        SourceSet main = project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getSourceSets()
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Provider<FileCollection> classes = project.provider(() -> main.getOutput()
                .getClassesDirs()
                .getAsFileTree()
                .matching(pattern -> pattern.exclude(nova.getCoverageExcludes().get())));
        TaskProvider<Test> test = project.getTasks().named(JavaPlugin.TEST_TASK_NAME, Test.class);
        project.getTasks().named("jacocoTestReport", JacocoReport.class, report -> {
            report.dependsOn(test);
            report.getReports().getXml().getRequired().set(true);
            report.getClassDirectories().setFrom(classes);
        });
        project.getTasks().named("jacocoTestCoverageVerification", JacocoCoverageVerification.class, verification -> {
            verification.dependsOn(test);
            verification.getClassDirectories().setFrom(classes);
            verification
                    .getViolationRules()
                    .rule(rule -> rule.limit(limit -> {
                        limit.setCounter("LINE");
                        limit.setValue("COVEREDRATIO");
                        limit.setMinimum(nova.getCoverageMinimum().get());
                    }));
        });
        test.configure(task -> task.finalizedBy("jacocoTestReport"));
    }

    private static void contract(Project project, NovaExtension nova) {
        project.getTasks().register("novaFormat", task -> {
            task.setGroup(GROUP);
            task.setDescription("Formats the code with the Nova formatter.");
            task.dependsOn("spotlessApply");
        });
        TaskProvider<Task> verify = project.getTasks().register("novaVerify", task -> {
            task.setGroup(GROUP);
            task.setDescription(
                    "Checks the format, runs Checkstyle, the tests and the coverage minimum. It is what CI runs.");
            task.dependsOn(
                    "spotlessCheck", "checkstyleMain", "checkstyleTest", "test", "jacocoTestCoverageVerification");
        });
        project.getTasks().named("check", check -> check.dependsOn(verify));
        project.getTasks().register("novaCommitLint", CommitLintTask.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Validates commit messages against Conventional Commits, for a range or a file.");
            task.getWorkingDirectory().set(project.getLayout().getProjectDirectory());
        });
        TaskProvider<InstallGitHooksTask> hooks = project.getTasks()
                .register("novaInstallGitHooks", InstallGitHooksTask.class, task -> {
                    task.setGroup(GROUP);
                    task.setDescription("Installs the commit-msg hook that validates each commit message.");
                    task.getWorkingDirectory().set(project.getLayout().getProjectDirectory());
                    task.getToolchainLocation().set(toolchainLocation());
                });
        // El hook se instala solo en el primer build local, nunca en CI, y solo desde el proyecto raíz.
        boolean ci = env(project, "CI") != null;
        if (project == project.getRootProject() && !ci) {
            project.getTasks()
                    .named(JavaPlugin.COMPILE_JAVA_TASK_NAME)
                    .configure(compile ->
                            compile.dependsOn(nova.getGitHooks().map(enabled -> enabled ? List.of(hooks) : List.of())));
        }
    }

    private static String toolchainLocation() {
        try {
            return Path.of(NovaQualityPlugin.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())
                    .toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Could not locate the Nova toolchain jar", e);
        }
    }

    private static String resource(String name) {
        try (InputStream in = NovaQualityPlugin.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("The Nova toolchain jar does not contain " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + name + " from the Nova toolchain jar", e);
        }
    }

    private static String env(Project project, String name) {
        return project.getProviders().environmentVariable(name).getOrNull();
    }
}
