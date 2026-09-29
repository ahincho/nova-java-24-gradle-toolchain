import org.gradle.api.plugins.quality.CheckstyleExtension
import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension

plugins {
    id("com.diffplug.spotless") version "8.10.3"
    id("org.owasp.dependencycheck") version "12.2.2" apply false
    id("org.cyclonedx.bom") version "3.4.1"
}

val repositoryUrl = "https://github.com/ahincho/nova-java-24-gradle-toolchain"
val catalog = versionCatalogs.named("libs")
val checkstyleConfig = file("nova-gradle-toolchain-quality/src/main/resources/pe/edu/nova/java/gradle/quality/checkstyle.xml")

// El toolchain no puede aplicarse a sí mismo, así que repite aquí lo que sus plugins aplican: el mismo
// formateador, la misma configuración de Checkstyle y las mismas banderas de javac.
spotless {
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint(catalog.findVersion("ktlint").get().requiredVersion)
    }
}

subprojects {
    group = "pe.edu.nova.java"
    version = rootProject.findProperty("version") as String

    apply(plugin = "java-gradle-plugin")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "checkstyle")
    apply(plugin = "jacoco")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "org.owasp.dependencycheck")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
        withSourcesJar()
        withJavadocJar()
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/*/java/**/*.java")
            palantirJavaFormat(catalog.findVersion("palantir-java-format").get().requiredVersion)
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    dependencies {
        "testImplementation"(platform(catalog.findLibrary("junit-bom").get()))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"(catalog.findLibrary("assertj-core").get())
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing", "-Werror"))
    }

    tasks.withType<Jar>().configureEach {
        manifest {
            attributes("Implementation-Title" to project.name, "Implementation-Version" to project.version)
        }
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        reports {
            xml.required.set(true)
        }
    }

    tasks.named<Javadoc>("javadoc") {
        (options as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:all", "-quiet")
            encoding = "UTF-8"
            charSet = "UTF-8"
        }
    }

    configure<CheckstyleExtension> {
        toolVersion = catalog.findVersion("checkstyle").get().requiredVersion
        config = resources.text.fromFile(checkstyleConfig)
        maxWarnings = 0
    }

    configure<DependencyCheckExtension> {
        // NVD_API_KEY y NOVA_OWASP_FAIL_ON_CVSS los pone reusable-owasp-check.yml. En local, sin
        // esas variables, el análisis nunca falla (11) y corre sin clave de NVD.
        failBuildOnCVSS = (System.getenv("NOVA_OWASP_FAIL_ON_CVSS") ?: "11").toFloat()
        nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""
        // reusable-owasp-check.yml restaura un mirror de NVD de menos de 24 horas; sin estas dos
        // líneas el plugin lo ignora y sincroniza NVD entero.
        autoUpdate = false
        data.directory = System.getenv("NOVA_OWASP_DATA_DIR")
            ?: "${System.getProperty("user.home")}/.dependency-check-data"
        scanConfigurations = listOf("compileClasspath", "runtimeClasspath")
        // En Java 25 File("").exists() es true: por eso se pide un archivo y no solo que la ruta exista.
        System
            .getenv("NOVA_OWASP_SUPPRESSIONS_FILE")
            ?.takeIf { it.isNotBlank() && File(it).isFile }
            ?.let { suppressionFiles.add(it) }
    }

    configure<PublishingExtension> {
        publications.withType<MavenPublication>().configureEach {
            pom {
                name.set(project.name)
                description.set(provider { project.description })
                url.set(repositoryUrl)
                licenses {
                    license {
                        name.set("Eclipse Public License 2.0")
                        url.set("https://www.eclipse.org/legal/epl-2.0/")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("ahincho")
                        name.set("Angel Eduardo Hincho Jove")
                    }
                }
                scm {
                    url.set(repositoryUrl)
                    connection.set("scm:git:$repositoryUrl.git")
                }
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/ahincho/nova-java-24-gradle-toolchain")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }

    configure<SigningExtension> {
        val gpgKeyId: String? = System.getenv("GPG_SIGNING_KEY_ID")
        val gpgKey: String? = System.getenv("GPG_SIGNING_KEY")
        val gpgPassword: String? = System.getenv("GPG_SIGNING_PASSWORD")

        if (gpgKeyId != null && gpgKey != null) {
            useInMemoryPgpKeys(gpgKeyId, gpgKey, gpgPassword ?: "")
            sign(the<PublishingExtension>().publications)
        }
    }
}
