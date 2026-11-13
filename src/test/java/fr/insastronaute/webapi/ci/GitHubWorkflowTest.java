package fr.insastronaute.webapi.ci;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class GitHubWorkflowTest {
    private static final Path WORKFLOW = Path.of(".github/workflows/build-workflow.yml");
    private static final Path DOCKERFILE = Path.of("Dockerfile");

    @Test
    void nothingInThePipelineTouchesProduction() throws IOException {
        // La garde ne repose pas sur l'absence de runner : elle repose sur l'absence de job.
        // Un runner self-hosted rebranche un jour ne doit pas ressusciter un deploiement que
        // personne n'attend plus.
        String workflow = Files.readString(WORKFLOW);

        assertThat(workflow).doesNotContain("deploy-production:", "X64, prod]");
    }

    @Test
    void everyActionIsPinnedByDigest() throws IOException {
        assertThat(Files.readString(WORKFLOW)).doesNotContainPattern("uses: [^\\s]+@v[0-9]");
    }

    @Test
    void backendBaseImageIsImmutable() throws IOException {
        String dockerfile = Files.readString(DOCKERFILE);

        assertThat(dockerfile)
                .startsWith("FROM eclipse-temurin:25-jre-ubi10-minimal@sha256:")
                .containsPattern("(?m)^FROM eclipse-temurin:25-jre-ubi10-minimal@sha256:[a-f0-9]{64}$");
    }
}
