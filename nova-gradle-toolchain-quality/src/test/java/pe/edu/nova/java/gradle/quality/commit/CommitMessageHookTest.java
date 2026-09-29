package pe.edu.nova.java.gradle.quality.commit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommitMessageHookTest {

    @TempDir
    Path directory;

    @Test
    void aValidMessagePasses() throws IOException {
        assertThat(run("feat: add the adapter\n")).isZero();
    }

    @Test
    void anInvalidMessageExitsWithItsOwnCodeAndSaysWhy() throws IOException {
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int status = CommitMessageHook.run(
                new String[] {write("Add the adapter\n").toString()},
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertThat(status).isEqualTo(CommitMessageHook.INVALID);
        assertThat(err.toString(StandardCharsets.UTF_8))
                .contains("no sigue Conventional Commits")
                .contains("el encabezado no sigue la forma");
    }

    @Test
    void theCommentsThatGitAddsAreNotPartOfTheMessage() throws IOException {
        String raw = """
                feat: add the adapter

                # Please enter the commit message for your changes.
                # On branch feat/adapter
                """;

        assertThat(run(raw)).isZero();
    }

    @Test
    void everythingAfterTheScissorsLineIsIgnored() {
        String raw = """
                feat: add the adapter

                # ------------------------ >8 ------------------------
                diff --git a/A.java b/A.java
                """;

        assertThat(CommitMessageHook.withoutComments(raw)).isEqualTo("feat: add the adapter\n");
    }

    @Test
    void aMissingFileIsNotAnInvalidMessage() {
        int status = CommitMessageHook.run(
                new String[] {directory.resolve("missing").toString()},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        assertThat(status).isEqualTo(2);
    }

    private int run(String message) throws IOException {
        return CommitMessageHook.run(
                new String[] {write(message).toString()},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    private Path write(String message) throws IOException {
        Path file = directory.resolve("COMMIT_EDITMSG");
        Files.writeString(file, message, StandardCharsets.UTF_8);
        return file;
    }
}
