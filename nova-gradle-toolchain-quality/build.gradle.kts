description = "Nova quality plugin: format, Checkstyle, tests, coverage and commit messages for every Java project."

dependencies {
    implementation(libs.spotless.plugin)
    implementation(libs.dependency.check.plugin)
    implementation(libs.cyclonedx.plugin)
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
