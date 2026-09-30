# nova-gradle-toolchain

**El toolchain de Java de Nova Platform:** plugins de convención de Gradle que traen las
herramientas en versiones fijas, sus configuraciones y un contrato de tareas. Es el equivalente de
`@ahincho/nova-nestjs-toolchain` para Java, y lo decide
[ADR-044](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-044-toolchain-de-java.md).

Un repositorio no nombra ninguna herramienta ni declara su versión. Cambiar de formateador, subir
Checkstyle o arreglar el bloque de OWASP se hace aquí y llega con una versión del plugin.

## Los plugins

| Plugin | Para quién | Qué aplica |
|---|---|---|
| `pe.edu.nova.java.quality` | todo proyecto Java | Java 25, formato, Checkstyle, pruebas, cobertura, validación de commits y su hook |
| `pe.edu.nova.java.library` | librerías y starters de la plataforma | `quality`, más `java-library`, los jars de fuentes y javadoc, la publicación con su POM, la firma, OWASP y el SBOM |
| `pe.edu.nova.java.spring-boot-service` | servicios Spring Boot | `quality`, más Spring Boot con su BOM, los starters de Nova, OWASP, el SBOM y la imagen |

El plugin de servicios no reusa el id de `nova-java-16-spring-boot-gradle-plugin`: el registro de Maven
de GitHub Packages no deja que otro repositorio publique su marcador. Un consumidor del repo 16 migra
cambiando una línea, `id("pe.edu.nova.java.spring-boot") version "1.0.3"` por
`id("pe.edu.nova.java.spring-boot-service") version "1.0.0"`. El plugin de Quarkus llega con el
catálogo de Plaza.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("https://maven.pkg.github.com/ahincho/nova-java-24-gradle-toolchain")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("NOVA_PACKAGES_READ_TOKEN") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// build.gradle.kts
plugins {
    id("pe.edu.nova.java.spring-boot-service") version "1.0.0"
}
```

El plugin agrega por su cuenta el registro de los paquetes de Nova, restringido a los grupos
`pe.edu.nova.*`, así que el `build.gradle.kts` de un servicio no declara repositorios.

## Una librería

Una librería publica en el GitHub Packages de su propio repositorio, que sale de `GITHUB_REPOSITORY`,
la variable que el CI siempre define. En un build local no existe, así que la librería se publica en
`mavenLocal` y no en GitHub Packages; para otro destino está la propiedad `nova.repository` en
`gradle.properties`. El POM lleva la licencia EPL-2.0, el autor y el repositorio, y la publicación se
firma cuando el CI trae la llave GPG.

En un repositorio de varios módulos, como una capacidad con su contrato y sus adaptadores, la raíz
aplica `quality` y cada módulo aplica `library`:

```kotlin
// build.gradle.kts de la raíz
plugins {
    id("pe.edu.nova.java.quality") version "1.1.0"
    id("pe.edu.nova.java.library") version "1.1.0" apply false
}

// build.gradle.kts de cada módulo
plugins {
    id("pe.edu.nova.java.library")
}
```

La raíz formatea sus propios scripts, instala el hook y junta el SBOM de todos los módulos en
`build/reports/cyclonedx`, donde lo busca el CI compartido.

## Las tareas

| Tarea | Qué corre |
|---|---|
| `novaFormat` | aplica el formato |
| `novaVerify` | formato, Checkstyle, pruebas y cobertura; es lo que corre el CI |
| `novaCommitLint` | valida los mensajes de commit de un rango o de un archivo |
| `novaInstallGitHooks` | instala el hook `commit-msg` |
| `novaSecurity` | OWASP y el SBOM; va aparte porque tarda |
| `novaDocker` | construye la imagen con el Dockerfile de la plataforma |
| `novaDockerEject` | escribe ese Dockerfile en el repositorio, para un pipeline que lo exige |
| `novaDockerNative` | construye la imagen nativa, si el servicio activó el modo nativo |

`check` depende de `novaVerify`, así que `./gradlew build` verifica lo mismo que el CI.

## Una herramienta, un papel

| Herramienta | Versión | Papel |
|---|---|---|
| Spotless con palantir-java-format | 8.10.3 y 2.100.0 | formatea el Java, con 4 espacios y 120 columnas, y lo corrige solo |
| ktlint | 1.8.0 | formatea los `*.gradle.kts` |
| Checkstyle | 14.3.0 | solo defectos, en `error` y con `maxWarnings = 0` |
| javac | Java 25 | `-Xlint:all -Werror` y `-parameters` |
| JaCoCo | 0.8.15 | exige el 80 % de líneas, el mismo número que Vitest en NestJS |
| OWASP Dependency-Check y CycloneDX | 12.2.2 y 3.4.1 | las vulnerabilidades conocidas y el SBOM |

Las versiones salen de [`gradle/libs.versions.toml`](gradle/libs.versions.toml), que es la única
fuente: el build las copia dentro del plugin.

El formato va siempre con fin de línea LF, como el `.gitattributes` de los repositorios de Nova. Sin
eso, en Windows y sin `.gitattributes`, el mismo archivo pasaba en CI y fallaba en local.

La configuración de Checkstyle está en el plugin y solo tiene defectos: imports que sobran, contratos
de `equals` y `hashCode`, comparar strings con `==`, un `catch` vacío, un `switch` que cae al siguiente
caso y las convenciones de nombres. Lo que un formateador puede arreglar no está.

Para las pruebas, el plugin trae JUnit, AssertJ y las versiones de Testcontainers, ArchUnit y
`nova-architecture-rules`. Un servicio escribe
`testImplementation("org.testcontainers:testcontainers-postgresql")`, sin versión.

## Lo que no se puede perder

Una bandera que falta no avisa: deja el análisis a medias y en verde. Por eso el plugin fija lo que
no se negocia:

- Checkstyle con `maxWarnings = 0` y javac con `-Werror`;
- la verificación de cobertura cuelga de `check`, no de una tarea que haya que acordarse de llamar;
- los jars son reproducibles: sin fechas y en orden fijo.

## Todo se puede ajustar, a la vista

```kotlin
nova {
    coverageMinimum = "0.90".toBigDecimal()
    coverageExcludes.add("**/legacy/**")
    gitHooks = false
}
```

Una regla de Checkstyle se suprime en el código con `@SuppressWarnings("checkstyle:<Regla>")`. Bajar
el mínimo o apagar algo se puede, pero queda escrito en el `build.gradle.kts` del repositorio.

## Los mensajes de commit

`novaCommitLint` valida Conventional Commits con las reglas de `@commitlint/config-conventional`:

- el tipo es uno de `build`, `chore`, `ci`, `docs`, `feat`, `fix`, `perf`, `refactor`, `revert`,
  `style` y `test`, en minúscula;
- el alcance es opcional y va en minúscula;
- la descripción empieza en minúscula y no termina en punto;
- el encabezado y cada línea del cuerpo tienen como mucho 100 caracteres;
- entre el encabezado y el cuerpo va una línea en blanco.

Se saltan los mensajes que escribe git o GitHub: los merges, los reverts y los `fixup!`.

```bash
./gradlew novaCommitLint --from=origin/main
./gradlew novaCommitLint --from=<base> --to=<head>
./gradlew novaCommitLint --message-file=.git/COMMIT_EDITMSG
```

**El hook `commit-msg` se instala solo en el primer build local, y nunca en CI.** No llama a Gradle,
que tarda segundos en arrancar: ejecuta con `java` una copia del jar del toolchain que queda en
`.git/hooks`. Si ya hay un `commit-msg` que no es de Nova, como el de lefthook, no lo reemplaza. Si no
encuentra `java`, deja pasar el commit y avisa, porque el CI valida los mismos mensajes.

Con esto, un repositorio Java deja de necesitar Node: `package.json`, `commitlint.config.js` y
`lefthook.yml` se borran al migrar.

## La imagen

El Dockerfile vive en el plugin, igual que en NestJS: JRE 25, el jar abierto por capas para que una
imagen nueva reuse las dependencias de la anterior, y un usuario sin privilegios. No se copia a cada
repositorio.

```bash
./gradlew novaDocker
./gradlew novaDocker --tag=plaza-orders:dev
./gradlew novaDocker --build-arg=JAVA_IMAGE=gcr.io/distroless/java25-debian13@sha256:...
./gradlew novaDockerEject
```

**La imagen final es distroless**
([ADR-046](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-046-imagenes-base-distroless.md)):
`gcr.io/distroless/java25-debian13`, el mismo JRE Temurin 25 sin shell ni gestor de paquetes. Se
descarga sin cuenta. Solo la etapa que abre el jar usa `eclipse-temurin:25-jre`, porque corre un `RUN`, y
esa etapa no llega a la imagen final. Frente a la de Ubuntu, la imagen pesa 250 MB en vez de 359 MB y
arranca igual.

Sin shell no hay `docker exec ... sh`: para depurar está `docker debug`, o la variante `:debug` de
distroless en `JAVA_IMAGE`. Para volver a la imagen de Ubuntu:
`--build-arg=JAVA_IMAGE=eclipse-temurin:25-jre`.

**Docker Hardened Images es la otra opción,** con la variante JRE 25 de `dhi.io/eclipse-temurin` en
`JAVA_IMAGE`. Es gratis, pero pide `docker login dhi.io` con una cuenta de Docker antes del build, y en
el CI, un secreto con esas credenciales.

Es una sola imagen para todos los ambientes: el ambiente llega como variable de entorno al arrancar,
así que lo que se probó en dev es lo que llega a prod. Expone el puerto 8080, la convención de los
servicios Java, y arranca con `java` como PID 1 para que el `SIGTERM` del orquestador dispare el
apagado ordenado. La imagen base es un argumento, para fijarla por digest: un tag cambia con el
tiempo, y dos builds del mismo commit podrían dar imágenes distintas.

El build corre fuera de Docker y la imagen solo copia el jar, así que no necesita el token del
registro. `novaDockerEject` escribe el Dockerfile con un encabezado que dice de dónde salió, y nunca
reemplaza uno que no es de Nova.

## El modo nativo

Un servicio también puede salir como ejecutable nativo de GraalVM
([ADR-045](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-045-imagen-nativa-junto-a-la-jvm.md)).
Es opcional, y se activa por servicio en `gradle.properties`:

```properties
nova.native=true
```

Con eso, el plugin aplica GraalVM Native Build Tools, que activa el procesamiento AOT de Spring, y
registra `novaDockerNative`. Sin la propiedad no cambia nada, y la imagen de la JVM sigue siendo la de
siempre.

```bash
./gradlew novaDockerNative
./gradlew novaDockerNative --build-arg=NATIVE_IMAGE_HEAP=8g
```

El ejecutable se compila dentro de Docker, en Linux, a partir del jar, así que funciona igual en
Windows y en el CI sin GraalVM instalado. La imagen es `<servicio>:<versión>-native`: el ejecutable sobre
`gcr.io/distroless/base-nossl-debian13`, que solo trae glibc, los certificados y las zonas horarias,
con el mismo usuario, el mismo puerto y el mismo arranque como PID 1 que la imagen de la JVM. El
compilador tiene un tope de 6 GB de heap. Un servicio mínimo compila en unos dos minutos, con un pico de
4 GB, en una máquina de 12 núcleos.

En Docker Hardened Images, la base equivalente es la variante `glibc-debian13` de `dhi.io/static`; la
de Alpine no sirve, porque trae musl y el ejecutable se enlaza con glibc.

Con GraalVM instalado, `./gradlew nativeCompile` compila el ejecutable de la propia máquina para ensayar.
Busca GraalVM en `GRAALVM_HOME`. En Windows pide también las herramientas de C++ de Visual Studio, y si
`native-image` no reconoce la versión instalada, se corre después de `vcvarsall.bat x64` o desde la
consola «x64 Native Tools Command Prompt». Ese `.exe` no corre en un contenedor Linux: la imagen se
construye siempre con `novaDockerNative`.

Con el modo nativo, el procesamiento AOT arranca la aplicación al compilar para preparar sus beans, y el
código que genera no pasa por `-Werror` ni por Checkstyle, porque no lo escribió nadie del equipo.

## Desarrollo

```bash
./gradlew build
```

Las pruebas corren cada plugin sobre proyectos reales con TestKit y el configuration cache encendido.
La de un servicio Spring Boot resuelve los starters de Nova, así que pide `GITHUB_ACTOR` y un
`GITHUB_TOKEN` con `read:packages`, y si hay Docker también construye la imagen. La de la imagen nativa
tarda minutos, así que corre solo con `NOVA_NATIVE_TEST=true`, y el CI la corre en el PR de cada versión.

El toolchain no puede aplicarse a sí mismo, así que su `build.gradle.kts` repite lo que sus plugins
aplican, y su CI valida los commits con el mismo hook sobre el jar recién construido.

## Licencia

[Eclipse Public License 2.0](LICENSE).
