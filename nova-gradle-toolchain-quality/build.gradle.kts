description = "Nova quality plugin: format, Checkstyle, tests, coverage and commit messages for every Java project."

dependencies {
    implementation(libs.spotless.plugin)
    implementation(libs.dependency.check.plugin)
    implementation(libs.cyclonedx.plugin)

    // OWASP marca CVE en las versiones que arrastran Spotless y el plugin de OWASP; se fijan los últimos
    // parches. Viajan en la metadata del plugin, así que también corrigen el classpath de cada consumidor.
    implementation(platform(libs.kotlin.bom))
    constraints {
        implementation(libs.httpclient5) { because("OWASP reports CVE-2026-71290 and CVE-2026-64607 on 5.6.1") }
        implementation(libs.httpclient5.cache) { because("OWASP reports CVE-2026-40542 on 5.6") }
        implementation(libs.httpcore5) { because("OWASP reports CVE-2026-54399 and CVE-2026-54428 on 5.4.2") }
        implementation(libs.httpcore5.h2) { because("OWASP reports CVE-2026-54399 and CVE-2026-54428 on 5.4") }
    }
}

// Las versiones del catálogo viajan dentro del plugin, para que un consumidor no declare ninguna.
val generateVersions by tasks.registering(WriteProperties::class) {
    destinationFile.set(layout.buildDirectory.file("generated/nova-versions/pe/edu/nova/java/gradle/quality/versions.properties"))
    val catalog = versionCatalogs.named("libs")
    catalog.versionAliases.forEach { alias ->
        property(alias, catalog.findVersion(alias).get().requiredVersion)
    }
}

sourceSets.main {
    resources.srcDir(generateVersions.map { layout.buildDirectory.dir("generated/nova-versions").get() })
}

gradlePlugin {
    plugins {
        create("novaQuality") {
            id = "pe.edu.nova.java.quality"
            implementationClass = "pe.edu.nova.java.gradle.quality.NovaQualityPlugin"
            displayName = "Nova Quality"
            description = project.description
        }
    }
}
