package pe.edu.nova.java.gradle.springboot;

import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.springframework.boot.gradle.plugin.SpringBootPlugin;
import org.springframework.boot.gradle.tasks.bundling.BootJar;
import pe.edu.nova.java.gradle.quality.NovaExtension;
import pe.edu.nova.java.gradle.quality.NovaQualityPlugin;
import pe.edu.nova.java.gradle.quality.NovaSecurity;
import pe.edu.nova.java.gradle.quality.NovaVersions;

/**
 * {@code pe.edu.nova.java.spring-boot}: un servicio Spring Boot de Nova (ADR-044).
 *
 * <p>Aplica {@code pe.edu.nova.java.quality} y encima Spring Boot con su BOM, los starters de máscara y
 * estándar de API de Nova, las pruebas de Spring con las reglas de arquitectura, OWASP con el SBOM y
 * la imagen del contenedor. Conserva el id del plugin del repositorio 16, así que un consumidor solo
 * sube la versión.
 */
public class NovaSpringBootPlugin implements Plugin<Project> {

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
            task.getDockerfile().set(project.getLayout().getBuildDirectory().file("nova-docker/Dockerfile"));
        });
        project.getTasks().register("novaDockerEject", DockerEjectTask.class, task -> {
            task.setGroup(NovaQualityPlugin.GROUP);
            task.setDescription("Writes the Nova Dockerfile into the repository, for a pipeline that requires it.");
            task.getDockerfile().set(project.getLayout().getProjectDirectory().file("Dockerfile"));
        });
    }
}
