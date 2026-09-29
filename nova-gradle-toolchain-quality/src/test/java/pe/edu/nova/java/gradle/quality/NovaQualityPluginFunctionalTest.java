package pe.edu.nova.java.gradle.quality;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** El plugin sobre proyectos reales, con TestKit y el configuration cache encendido. */
class NovaQualityPluginFunctionalTest {

    private static final String GREETER = """
            package sample;

            public final class Greeter {
                public String greet(String name) {
                    return "Hola, " + name;
                }
            }
            """;

    private static final String GREETER_TEST = """
            package sample;

            import static org.assertj.core.api.Assertions.assertThat;

            import org.junit.jupiter.api.Test;

            class GreeterTest {
                @Test
                void greetsByName() {
                    assertThat(new Greeter().greet("Angel")).isEqualTo("Hola, Angel");
                }
            }
            """;

    @TempDir
    Path directory;

    @Test
    void aWellFormedProjectPassesNovaVerify() {
        TestProject project = TestProject.quality(directory)
                .write("src/main/java/sample/Greeter.java", GREETER)
                .write("src/test/java/sample/GreeterTest.java", GREETER_TEST);

        BuildResult result = project.build("novaVerify");

        assertThat(result.task(":spotlessCheck").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":checkstyleMain").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":test").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":jacocoTestCoverageVerification").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void codeOutOfFormatFailsAndNovaFormatFixesIt() {
        TestProject project = TestProject.quality(directory)
                .write(
                        "src/main/java/sample/Greeter.java",
                        "package sample;\npublic final class Greeter{public String greet(String name){return \"Hola, \"+name;}}\n")
                .write("src/test/java/sample/GreeterTest.java", GREETER_TEST);

        BuildResult failed = project.fail("spotlessCheck");
        assertThat(failed.getOutput()).contains("Greeter.java").contains("format violations");

        project.build("novaFormat");

        assertThat(project.read("src/main/java/sample/Greeter.java"))
                .contains("    public String greet(String name) {");
        project.build("novaVerify");
    }

    @Test
    void aDefectFailsCheckstyle() {
        TestProject project = TestProject.quality(directory).write("src/main/java/sample/Names.java", """
                        package sample;

                        public final class Names {
                            public boolean isAdmin(String name) {
                                return name == "admin";
                            }
                        }
                        """);

        BuildResult result = project.fail("checkstyleMain");

        assertThat(result.getOutput()).contains("StringLiteralEquality");
    }

    @Test
    void aCompilerWarningFailsTheBuild() {
        TestProject project = TestProject.quality(directory).write("src/main/java/sample/Legacy.java", """
                        package sample;

                        public final class Legacy {
                            public Integer boxed() {
                                return new Integer(1);
                            }
                        }
                        """);

        BuildResult result = project.fail("compileJava");

        assertThat(result.getOutput()).contains("warnings found and -Werror specified");
    }

    @Test
    void coverageUnderTheMinimumFailsTheBuild() {
        TestProject project = TestProject.quality(directory)
                .write("src/main/java/sample/Greeter.java", """
                        package sample;

                        public final class Greeter {
                            public String greet(String name) {
                                return "Hola, " + name;
                            }

                            public String farewell(String name) {
                                String message = "Chau, " + name;
                                return message.strip();
                            }
                        }
                        """)
                .write("src/test/java/sample/GreeterTest.java", GREETER_TEST);

        BuildResult result = project.fail("jacocoTestCoverageVerification");

        assertThat(result.getOutput()).contains("lines covered ratio is").contains("expected minimum is 0.80");
    }

    @Test
    void aRepositoryCanLowerTheMinimumInItsOwnBuild() {
        TestProject project = new TestProject(directory, """
                        plugins {
                            id("pe.edu.nova.java.quality")
                        }

                        nova {
                            coverageMinimum = "0.10".toBigDecimal()
                        }
                        """)
                .write("src/main/java/sample/Greeter.java", """
                        package sample;

                        public final class Greeter {
                            public String greet(String name) {
                                return "Hola, " + name;
                            }

                            public String farewell(String name) {
                                String message = "Chau, " + name;
                                return message.strip();
                            }
                        }
                        """)
                .write("src/test/java/sample/GreeterTest.java", GREETER_TEST);

        BuildResult result = project.build("jacocoTestCoverageVerification");

        assertThat(result.task(":jacocoTestCoverageVerification").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void commitLintReportsEveryMessageOutOfTheConvention() {
        TestProject project = TestProject.quality(directory).write("README.md", "sample\n");
        project.git("init", "-q", "-b", "main");
        project.git("add", "-A");
        project.git("commit", "-q", "--no-verify", "-m", "chore: bootstrap the sample");
        String base = project.gitOutput("rev-parse", "HEAD");
        project.write("a.txt", "a\n").git("add", "-A");
        project.git("commit", "-q", "--no-verify", "-m", "feat: add a");
        project.write("b.txt", "b\n").git("add", "-A");
        project.git("commit", "-q", "--no-verify", "-m", "Added b.");

        BuildResult result = project.fail("novaCommitLint", "--from=" + base);

        assertThat(result.getOutput())
                .contains("Added b.")
                .contains("el encabezado no sigue la forma")
                .doesNotContain("feat: add a");
    }

    @Test
    void commitLintPassesWhenEveryMessageFollowsTheConvention() {
        TestProject project = TestProject.quality(directory).write("README.md", "sample\n");
        project.git("init", "-q", "-b", "main");
        project.git("add", "-A");
        project.git("commit", "-q", "--no-verify", "-m", "chore: bootstrap the sample");
        String base = project.gitOutput("rev-parse", "HEAD");
        project.write("a.txt", "a\n").git("add", "-A");
        project.git("commit", "-q", "--no-verify", "-m", "feat(sample): add a\n\nWhy a matters.");

        BuildResult result = project.build("novaCommitLint", "--from=" + base);

        assertThat(result.getOutput()).contains("1 mensajes de commit revisados");
    }

    @Test
    void theHookIsInstalledByALocalBuildAndRejectsABadMessage() {
        TestProject project = TestProject.quality(directory).write("src/main/java/sample/Greeter.java", GREETER);
        project.git("init", "-q", "-b", "main");

        project.build("compileJava");

        assertThat(project.read(".git/hooks/commit-msg")).contains("nova-toolchain");
        project.git("add", "-A");
        assertThat(project.git("commit", "-q", "-m", "Added the greeter.")).isNotZero();
        assertThat(project.git("commit", "-q", "-m", "feat: add the greeter")).isZero();
    }

    @Test
    void theHookIsNotInstalledInCi() {
        TestProject project = TestProject.quality(directory).inCi().write("src/main/java/sample/Greeter.java", GREETER);
        project.git("init", "-q", "-b", "main");

        project.build("compileJava");

        assertThat(project.exists(".git/hooks/commit-msg")).isFalse();
    }

    @Test
    void aHookThatIsNotFromNovaIsLeftAlone() {
        TestProject project = TestProject.quality(directory).write("src/main/java/sample/Greeter.java", GREETER);
        project.git("init", "-q", "-b", "main");
        project.write(".git/hooks/commit-msg", "#!/bin/sh\nlefthook run commit-msg\n");

        BuildResult result = project.build("compileJava");

        assertThat(project.read(".git/hooks/commit-msg")).contains("lefthook");
        assertThat(result.getOutput()).contains("no es de Nova");
    }

    @Test
    void theRootOfAMultiModuleBuildAggregatesTheSbom() {
        TestProject project = TestProject.quality(directory)
                .write("settings.gradle.kts", "rootProject.name = \"sample\"\ninclude(\"module\")\n")
                .write("module/build.gradle.kts", "")
                .write("build.gradle.kts", """
                        plugins {
                            id("pe.edu.nova.java.quality")
                        }

                        println("SBOM=${pluginManager.hasPlugin("org.cyclonedx.bom")}")
                        """);

        assertThat(project.build("help").getOutput()).contains("SBOM=true");
    }

    @Test
    void aSingleProjectLeavesTheSbomToItsPlugin() {
        TestProject project = TestProject.quality(directory).write("build.gradle.kts", """
                plugins {
                    id("pe.edu.nova.java.quality")
                }

                println("SBOM=${pluginManager.hasPlugin("org.cyclonedx.bom")}")
                """);

        assertThat(project.build("help").getOutput()).contains("SBOM=false");
    }
}
