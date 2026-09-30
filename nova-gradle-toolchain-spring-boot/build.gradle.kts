description = "Nova Spring Boot plugin: the quality plugin plus Spring Boot, the Nova starters, security and the container image."

dependencies {
    implementation(project(":nova-gradle-toolchain-quality"))
    implementation(libs.spring.boot.plugin)
    // Se aplica solo cuando el servicio pide el modo nativo (ADR-045).
    implementation(libs.native.build.tools.plugin)
}

gradlePlugin {
    plugins {
        create("novaSpringBoot") {
            // No reusa el id del repo 16: su marcador pertenece a ese repositorio (ADR-044).
            id = "pe.edu.nova.java.spring-boot-service"
            implementationClass = "pe.edu.nova.java.gradle.springboot.NovaSpringBootPlugin"
            displayName = "Nova Spring Boot"
            description = project.description
        }
    }
}
