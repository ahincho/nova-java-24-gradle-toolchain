description = "Nova Spring Boot plugin: the quality plugin plus Spring Boot, the Nova starters, security and the container image."

dependencies {
    implementation(project(":nova-gradle-toolchain-quality"))
    implementation(libs.spring.boot.plugin)
}

gradlePlugin {
    plugins {
        create("novaSpringBoot") {
            // Conserva el id del plugin que publicaba nova-java-16-spring-boot-gradle-plugin (ADR-044).
            id = "pe.edu.nova.java.spring-boot"
            implementationClass = "pe.edu.nova.java.gradle.springboot.NovaSpringBootPlugin"
            displayName = "Nova Spring Boot"
            description = project.description
        }
    }
}
