package pe.edu.nova.java.gradle.library;

import java.util.Map;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.plugins.signing.SigningExtension;
import org.gradle.plugins.signing.SigningPlugin;
import pe.edu.nova.java.gradle.quality.NovaQualityPlugin;
import pe.edu.nova.java.gradle.quality.NovaSecurity;

/**
 * {@code pe.edu.nova.java.library}: una librería o un starter de la plataforma (ADR-044).
 *
 * <p>Aplica {@code pe.edu.nova.java.quality} y encima {@code java-library}, los jars de fuentes y de
 * javadoc, la publicación en GitHub Packages con su POM, la firma y OWASP con el SBOM.
 *
 * <p>El repositorio de GitHub sale de {@code GITHUB_REPOSITORY}, que el CI siempre define, o de la
 * propiedad {@code nova.repository} en {@code gradle.properties}. Sin ninguna de las dos, en un build
 * local, la librería se publica en {@code mavenLocal} pero no en GitHub Packages.
 */
public class NovaLibraryPlugin implements Plugin<Project> {

    /** El nombre de la publicación, el mismo que usaban las librerías antes del toolchain. */
    public static final String PUBLICATION = "mavenJava";

    /** La propiedad de Gradle con el repositorio, como {@code ahincho/nova-java-23-secrets}. */
    public static final String REPOSITORY_PROPERTY = "nova.repository";

    private static final String LICENSE = "Eclipse Public License 2.0";
    private static final String LICENSE_URL = "https://www.eclipse.org/legal/epl-2.0/";

    /** Crea el plugin; lo instancia Gradle. */
    public NovaLibraryPlugin() {}

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NovaQualityPlugin.class);
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        project.getPluginManager().apply(MavenPublishPlugin.class);
        project.getPluginManager().apply(SigningPlugin.class);
        NovaSecurity.apply(project);

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.withSourcesJar();
        java.withJavadocJar();
        project.getTasks().withType(Javadoc.class).configureEach(javadoc -> {
            StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) javadoc.getOptions();
            options.addStringOption("Xdoclint:all", "-quiet");
            options.setEncoding("UTF-8");
            options.setCharSet("UTF-8");
        });
        String name = project.getName();
        Provider<String> version = project.provider(() -> String.valueOf(project.getVersion()));
        project.getTasks()
                .withType(Jar.class)
                .configureEach(jar -> jar.manifest(manifest ->
                        manifest.attributes(Map.of("Implementation-Title", name, "Implementation-Version", version))));

        // Se lee al configurar, porque de él depende si existe el repositorio de publicación. Los dos son
        // entradas del configuration cache: si cambian, Gradle vuelve a configurar.
        String repository = project.getProviders()
                .gradleProperty(REPOSITORY_PROPERTY)
                .orElse(project.getProviders().environmentVariable("GITHUB_REPOSITORY"))
                .getOrNull();
        publishing(project, repository);
        signing(project);
    }

    private static void publishing(Project project, String repository) {
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        publishing.getPublications().register(PUBLICATION, MavenPublication.class, publication -> {
            publication.from(project.getComponents().getByName("java"));
            publication.pom(pom -> pom(project, pom, repository));
        });
        if (repository == null) {
            return;
        }
        publishing.getRepositories().maven(maven -> {
            maven.setName("GitHubPackages");
            maven.setUrl("https://maven.pkg.github.com/" + repository);
            maven.credentials(credentials -> {
                credentials.setUsername(env(project, "GITHUB_ACTOR"));
                credentials.setPassword(env(project, "GITHUB_TOKEN"));
            });
        });
    }

    private static void pom(Project project, MavenPom pom, String repository) {
        pom.getName().convention(project.getName());
        pom.getDescription().convention(project.provider(project::getDescription));
        pom.licenses(licenses -> licenses.license(license -> {
            license.getName().set(LICENSE);
            license.getUrl().set(LICENSE_URL);
            license.getDistribution().set("repo");
        }));
        pom.developers(developers -> developers.developer(developer -> {
            developer.getId().set("ahincho");
            developer.getName().set("Angel Eduardo Hincho Jove");
        }));
        if (repository != null) {
            String url = "https://github.com/" + repository;
            pom.getUrl().convention(url);
            pom.scm(scm -> {
                scm.getUrl().convention(url);
                scm.getConnection().convention("scm:git:" + url + ".git");
            });
        }
    }

    private static void signing(Project project) {
        // Firma solo si el CI trae la llave; en local los artefactos salen sin firma.
        Provider<String> keyId = project.getProviders().environmentVariable("GPG_SIGNING_KEY_ID");
        Provider<String> key = project.getProviders().environmentVariable("GPG_SIGNING_KEY");
        if (!keyId.isPresent() || !key.isPresent()) {
            return;
        }
        String password = env(project, "GPG_SIGNING_PASSWORD");
        SigningExtension signing = project.getExtensions().getByType(SigningExtension.class);
        signing.useInMemoryPgpKeys(keyId.get(), key.get(), password != null ? password : "");
        signing.sign(
                project.getExtensions().getByType(PublishingExtension.class).getPublications());
    }

    private static String env(Project project, String name) {
        return project.getProviders().environmentVariable(name).getOrNull();
    }
}
