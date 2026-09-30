package pe.edu.nova.java.gradle.springboot;

import java.util.List;
import org.graalvm.buildtools.gradle.NativeImagePlugin;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.springframework.boot.gradle.plugin.SpringBootPlugin;
import org.springframework.boot.gradle.tasks.bundling.BootJar;
import pe.edu.nova.java.gradle.quality.NovaExtension;
import pe.edu.nova.java.gradle.quality.NovaQualityPlugin;
import pe.edu.nova.java.gradle.quality.NovaSecurity;
import pe.edu.nova.java.gradle.quality.NovaVersions;

/**
 * {@code pe.edu.nova.java.spring-boot-service}: un servicio Spring Boot de Nova (ADR-044).
 *
 * <p>Aplica {@code pe.edu.nova.java.quality} y encima Spring Boot con su BOM, los starters de máscara y
 * estándar de API de Nova, las pruebas de Spring con las reglas de arquitectura, OWASP con el SBOM y
 * la imagen del contenedor. Reemplaza al plugin {@code pe.edu.nova.java.spring-boot} del repositorio
 * 16, que no se puede publicar desde aquí.
 *
 * <p>Con {@code nova.native=true} en {@code gradle.properties}, suma el modo nativo de ADR-045: GraalVM
 * Native Build Tools, el procesamiento AOT de Spring y la imagen nativa.
 */
public class NovaSpringBootPlugin implements Plugin<Project> {

    /** La propiedad de Gradle que activa el modo nativo, en {@code gradle.properties} (ADR-045). */
    public static final String NATIVE_PROPERTY = "nova.native";

    /**
     * El tope de heap del compilador nativo, el mismo en local y en el Dockerfile. Sin él, native-image
     * toma buena parte de la memoria de la máquina.
     */
    static final String NATIVE_IMAGE_HEAP = "6g";

    /** Crea el plugin; lo instancia Gradle. */
    public NovaSpringBootPlugin() {}

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NovaQualityPlugin.class);
        project.getPluginManager().apply(SpringBootPlugin.class);
        NovaSecurity.apply(project);

        NovaVersions versions = NovaVersions.load();
        DependencyHandler dependencies = project.getDependencies();
        String implementation = JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME;
        String testImplementation = JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME;
        dependencies.add(implementation, dependencies.platform(SpringBootPlugin.BOM_COORDINATES));
        dependencies.add(
                implementation,
                "pe.edu.nova.java.starters:nova-mask-spring-boot-starter:"
                        + versions.of("nova.mask.spring.boot.starter"));
        dependencies.add(
                implementation,
                "pe.edu.nova.java.starters:nova-api-standard-spring-boot-starter:"
                        + versions.of("nova.api.standard.spring.boot.starter"));
        dependencies.add(testImplementation, "org.springframework.boot:spring-boot-starter-test");
        dependencies.add(testImplementation, "pe.edu.nova.java.libs:nova-architecture-rules");

        project.getExtensions()
                .getByType(NovaExtension.class)
                .getCoverageExcludes()
                .convention(List.of("**/*Application.class", "**/generated/**"));

        docker(project);
        // Se lee al configurar, porque de él depende qué plugins se aplican; es una entrada del
        // configuration cache.
        boolean nativeMode = project.getProviders()
                .gradleProperty(NATIVE_PROPERTY)
                .map(Boolean::parseBoolean)
                .getOrElse(false);
        if (nativeMode) {
            nativeImage(project);
        }
    }

    private static void docker(Project project) {
        project.getTasks().register("novaDocker", DockerBuildTask.class, task -> {
            task.setGroup(NovaQualityPlugin.GROUP);
            task.setDescription("Builds the container image with the Nova Dockerfile.");
            TaskProvider<BootJar> bootJar =
                    project.getTasks().named(SpringBootPlugin.BOOT_JAR_TASK_NAME, BootJar.class);
            task.dependsOn(bootJar);
            task.getJar().set(bootJar.flatMap(BootJar::getArchiveFile));
            task.getImage().convention(project.getName() + ":" + project.getVersion());
            task.getBuildArgs().convention(List.of());
            task.getTemplate().set(NovaDockerfile.JVM);
            task.getDockerfile().set(project.getLayout().getBuildDirectory().file("nova-docker/Dockerfile"));
        });
        project.getTasks().register("novaDockerEject", DockerEjectTask.class, task -> {
            task.setGroup(NovaQualityPlugin.GROUP);
            task.setDescription("Writes the Nova Dockerfile into the repository, for a pipeline that requires it.");
            task.getDockerfile().set(project.getLayout().getProjectDirectory().file("Dockerfile"));
        });
    }

    private static void nativeImage(Project project) {
        // Native Build Tools activa el procesamiento AOT de Spring: el bootJar lleva el código generado y
        // los metadatos que native-image necesita. Trae además nativeCompile, que compila con el GraalVM de
        // la máquina para ensayar; la imagen se compila siempre en Docker.
        project.getPluginManager().apply(NativeImagePlugin.class);
        project.getExtensions()
                .getByType(GraalVMExtension.class)
                .getBinaries()
                .configureEach(binary -> binary.jvmArgs("-Xmx" + NATIVE_IMAGE_HEAP));
        // El código que genera el procesamiento AOT no es del servicio: Spring lo escribe con tipos crudos,
        // y con -Werror el build fallaría sin que el servicio tenga un defecto.
        project.getTasks()
                .withType(JavaCompile.class)
                .matching(compile -> compile.getName().startsWith("compileAot"))
                .configureEach(compile ->
                        compile.getOptions().getCompilerArgs().removeAll(NovaQualityPlugin.STRICT_COMPILER_ARGS));
        project.getTasks().register("novaDockerNative", DockerBuildTask.class, task -> {
            task.setGroup(NovaQualityPlugin.GROUP);
            task.setDescription("Builds the native image of the service with the Nova Dockerfile.");
            TaskProvider<BootJar> bootJar =
                    project.getTasks().named(SpringBootPlugin.BOOT_JAR_TASK_NAME, BootJar.class);
            task.dependsOn(bootJar);
            task.getJar().set(bootJar.flatMap(BootJar::getArchiveFile));
            task.getImage().convention(project.getName() + ":" + project.getVersion() + "-native");
            task.getBuildArgs().convention(List.of());
            task.getTemplate().set(NovaDockerfile.NATIVE);
            task.getDockerfile().set(project.getLayout().getBuildDirectory().file("nova-docker-native/Dockerfile"));
        });
    }
}
