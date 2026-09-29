description =
    "Nova library plugin: the quality plugin plus java-library, sources and javadoc jars, publishing, signing, OWASP and the SBOM."

dependencies {
    implementation(project(":nova-gradle-toolchain-quality"))
}

gradlePlugin {
    plugins {
        create("novaLibrary") {
            id = "pe.edu.nova.java.library"
            implementationClass = "pe.edu.nova.java.gradle.library.NovaLibraryPlugin"
            displayName = "Nova Library"
            description = project.description
        }
    }
}
