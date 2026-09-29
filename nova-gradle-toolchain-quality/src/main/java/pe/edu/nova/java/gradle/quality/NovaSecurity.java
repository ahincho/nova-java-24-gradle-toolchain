package pe.edu.nova.java.gradle.quality;

import java.io.File;
import java.util.List;
import org.gradle.api.Project;
import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension;

/**
 * OWASP Dependency-Check y el SBOM de CycloneDX, con la tarea {@code novaSecurity}.
 *
 * <p>No es parte de {@code novaVerify} porque tarda: lo aplican los plugins de librería y de servicio,
 * y el CI lo corre en su propio job.
 */
public final class NovaSecurity {

    private NovaSecurity() {}

    /**
     * Aplica los dos análisis a un proyecto.
     *
     * @param project el proyecto
     */
    public static void apply(Project project) {
        project.getPluginManager().apply("org.owasp.dependencycheck");
        project.getPluginManager().apply("org.cyclonedx.bom");
        DependencyCheckExtension check = project.getExtensions().getByType(DependencyCheckExtension.class);
        // NVD_API_KEY y NOVA_OWASP_FAIL_ON_CVSS los pone reusable-owasp-check.yml. En local, sin esas
        // variables, el análisis nunca falla (11) y corre sin clave de NVD.
        String failOn = env(project, "NOVA_OWASP_FAIL_ON_CVSS");
        check.setFailBuildOnCVSS(Float.parseFloat(failOn != null ? failOn : "11"));
        String apiKey = env(project, "NVD_API_KEY");
        check.getNvd().setApiKey(apiKey != null ? apiKey : "");
        // El CI restaura un mirror de NVD de menos de 24 horas; sin estas dos líneas el plugin lo ignora
        // y sincroniza NVD entero.
        check.setAutoUpdate(false);
        String dataDirectory = env(project, "NOVA_OWASP_DATA_DIR");
        check.getData()
                .setDirectory(
                        dataDirectory != null
                                ? dataDirectory
                                : System.getProperty("user.home") + "/.dependency-check-data");
        // Solo lo que recibe el consumidor: las herramientas del build y las pruebas no viajan.
        check.setScanConfigurations(List.of("compileClasspath", "runtimeClasspath"));
        // En Java 25 File("").exists() es true, así que se pide un archivo y no solo una ruta.
        String suppressions = env(project, "NOVA_OWASP_SUPPRESSIONS_FILE");
        if (suppressions != null && !suppressions.isBlank() && new File(suppressions).isFile()) {
            check.getSuppressionFiles().add(suppressions);
        }
        project.getTasks().register("novaSecurity", task -> {
            task.setGroup(NovaQualityPlugin.GROUP);
            task.setDescription("Checks the dependencies against known vulnerabilities and writes the SBOM.");
            task.dependsOn("dependencyCheckAnalyze", "cyclonedxBom");
        });
    }

    private static String env(Project project, String name) {
        return project.getProviders().environmentVariable(name).getOrNull();
    }
}
