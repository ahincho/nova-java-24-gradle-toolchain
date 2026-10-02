# Changelog

## [2.1.0](https://github.com/ahincho/nova-java-24-gradle-toolchain/compare/v2.0.0...v2.1.0) (2026-10-02)


### Features

* bring nova-architecture-rules 1.2.0 to every service ([638159c](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/638159c4b2f2723264c18b5565b61c123a234b46))
* bring the MockMvc test starter to every Spring Boot service ([36f8b7e](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/36f8b7e730f7d07335db741eeacac383185ac640))

## [2.0.0](https://github.com/ahincho/nova-java-24-gradle-toolchain/compare/v1.3.1...v2.0.0) (2026-10-01)


### ⚠ BREAKING CHANGES

* nova-api-standard-spring-boot-starter 3.0.0 changes the error responses of every service. The README has the recipe.

### Features

* bring the 3.0.0 starters with the layered errors of ADR-031 ([afdf53e](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/afdf53ee593dac5bbf9adc48017f45c01ac66f4f))


### Bug Fixes

* **deps:** bring the 3.0.1 starters that work in a native image ([209620f](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/209620fb111088c9b7232b84f080b9b3f6221028))

## [1.3.1](https://github.com/ahincho/nova-java-24-gradle-toolchain/compare/v1.3.0...v1.3.1) (2026-09-30)


### Bug Fixes

* pin the Tomcat and Jackson patches of every service ([54f0de7](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/54f0de7e71fb3fdab64ea030118a9b41ce054907))

## [1.3.0](https://github.com/ahincho/nova-java-24-gradle-toolchain/compare/v1.2.0...v1.3.0) (2026-09-30)


### Features

* carry the jar SBOM inside the native image ([7cb5a25](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/7cb5a251849764a8f690d85229113cfc59cae83f))
* run both service images on distroless ([9b6c944](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/9b6c944bd9cbf46b0cbee54895e102c9429c6ce3))

## [1.2.0](https://github.com/ahincho/nova-java-24-gradle-toolchain/compare/v1.1.1...v1.2.0) (2026-09-30)


### Features

* build a native image of a Spring Boot service ([d898689](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/d898689ecf5b9a4fa9bd822ddd4758e932ccb896))
* keep Checkstyle on the code the project writes ([f77c7c6](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/f77c7c6415ab9115dc62d06a86ccd55ac4f08a40))

## [1.1.1](https://github.com/ahincho/nova-java-24-gradle-toolchain/compare/v1.1.0...v1.1.1) (2026-09-29)


### Bug Fixes

* run the plugins on any JVM that Gradle 9 supports ([c0924b0](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/c0924b0b9abe436924834e2a03348b2dddba3ce6))

## [1.1.0](https://github.com/ahincho/nova-java-24-gradle-toolchain/compare/v1.0.1...v1.1.0) (2026-09-29)


### Features

* add the library plugin ([e3651c3](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/e3651c3677e03ecfd299059ae951e3130495c2cf))
* aggregate the SBOM at the root of a multi-module build ([25f0f99](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/25f0f99c88a740bc2f44d5e159999ad0bec6df5e))

## [1.0.1](https://github.com/ahincho/nova-java-24-gradle-toolchain/compare/v1.0.0...v1.0.1) (2026-09-29)


### Bug Fixes

* print the commit hook messages in UTF-8 ([6110add](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/6110add4dc9661cedf32758e8d07dbb96a5b0f1a))

## 1.0.0 (2026-09-29)


### Features

* add the quality and spring-boot convention plugins ([483030a](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/483030a30018f4365a6f25ac0ead88edb711af98))
* publish the Spring Boot plugin as pe.edu.nova.java.spring-boot-service ([6ff9962](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/6ff9962030ca87b9e677680ac9c3a728a7b6783c))
* take the image base as an argument and expose the platform port ([d506884](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/d506884ccdbc868462663d16086e5ffb5ea1c4e7))


### Bug Fixes

* make the Gradle wrapper executable ([f7b3c3a](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/f7b3c3aef7fd68aff58696989b2806de0f611ec3))
* pin the patched HTTP and Kotlin libraries on the plugin classpath ([1801bf6](https://github.com/ahincho/nova-java-24-gradle-toolchain/commit/1801bf63f0fc8a2416ebeaa09d1fe4e155e1c4fd))
