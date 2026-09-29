rootProject.name = "nova-gradle-toolchain"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Spotless, OWASP y CycloneDX publican sus plugins en el portal de Gradle.
        gradlePluginPortal()
    }
}

include("nova-gradle-toolchain-quality")
include("nova-gradle-toolchain-library")
include("nova-gradle-toolchain-spring-boot")
