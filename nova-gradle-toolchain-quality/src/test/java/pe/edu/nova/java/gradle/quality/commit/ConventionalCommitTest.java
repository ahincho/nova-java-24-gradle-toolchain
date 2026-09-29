package pe.edu.nova.java.gradle.quality.commit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConventionalCommitTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "feat: add the AWS Secrets Manager adapter",
                "fix(deps): bump nova-secrets to 1.0.1",
                "chore(main): release 1.0.0",
                "feat!: drop the Java 21 build",
                "refactor(orders)!: split the order service",
                "docs: decide the Java toolchain as Gradle convention plugins\n\nThe body explains why.",
                "feat: place orders\n\nBody.\n\nBREAKING CHANGE: the header X-Customer-Id is required"
            })
    void aConventionalMessageIsValid(String message) {
        assertThat(ConventionalCommit.problems(message)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Merge pull request #6 from ahincho/feat/aws-secrets-manager-adapter",
                "Merge branch 'main' into feat/orders",
                "Merge remote-tracking branch 'origin/main'",
                "Revert \"feat: add the adapter\"",
                "fixup! feat: add the adapter",
                "Initial commit"
            })
    void aMessageWrittenByGitIsIgnored(String message) {
        assertThat(ConventionalCommit.isIgnored(message)).isTrue();
        assertThat(ConventionalCommit.problems(message)).isEmpty();
    }

    @Test
    void anEmptyMessageIsRejected() {
        assertThat(ConventionalCommit.problems("  \n")).containsExactly("el mensaje está vacío");
    }

    @Test
    void aHeaderWithoutATypeIsRejected() {
        assertThat(ConventionalCommit.problems("add the adapter"))
                .containsExactly("el encabezado no sigue la forma <tipo>(<alcance>): <descripción>");
    }

    @Test
    void anUnknownTypeListsTheValidOnes() {
        assertThat(ConventionalCommit.problems("feature: add the adapter"))
                .containsExactly("el tipo 'feature' no existe; los válidos son "
                        + "build, chore, ci, docs, feat, fix, perf, refactor, revert, style, test");
    }

    @Test
    void theTypeAndTheScopeAreLowerCase() {
        assertThat(ConventionalCommit.problems("Feat(Orders): add the adapter"))
                .containsExactly(
                        "el tipo tiene que ir en minúscula: Feat", "el alcance tiene que ir en minúscula: Orders");
    }

    @Test
    void anEmptyScopeIsRejected() {
        assertThat(ConventionalCommit.problems("feat(): add the adapter"))
                .containsExactly("el alcance entre paréntesis no puede ir vacío");
    }

    @Test
    void theDescriptionIsRequired() {
        assertThat(ConventionalCommit.problems("feat: "))
                .containsExactly("falta la descripción después de los dos puntos");
    }

    @Test
    void theDescriptionStartsLowerCaseAndHasNoFinalPeriod() {
        assertThat(ConventionalCommit.problems("feat: Add the adapter."))
                .containsExactly(
                        "la descripción tiene que empezar en minúscula", "la descripción no puede terminar en punto");
    }

    @Test
    void theHeaderHasAtMostOneHundredCharacters() {
        String header = "feat: " + "a".repeat(95);

        assertThat(ConventionalCommit.problems(header)).containsExactly("el encabezado pasa de 100 caracteres");
    }

    @Test
    void theBodyStartsAfterABlankLine() {
        assertThat(ConventionalCommit.problems("feat: add the adapter\nThe body."))
                .containsExactly("falta una línea en blanco entre el encabezado y el cuerpo");
    }

    @Test
    void aBodyLineHasAtMostOneHundredCharacters() {
        String message = "feat: add the adapter\n\n" + "b".repeat(101);

        assertThat(ConventionalCommit.problems(message)).containsExactly("la línea 3 pasa de 100 caracteres");
    }

    @Test
    void windowsLineEndingsAreAccepted() {
        assertThat(ConventionalCommit.problems("feat: add the adapter\r\n\r\nThe body.\r\n"))
                .isEmpty();
    }
}
