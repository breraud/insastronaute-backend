package fr.insastronaute.webapi.common.json;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aucun DTO d'API ne porte l'arbre JSON de Jackson 2.
 *
 * <p>Deux Jackson cohabitent : la couche HTTP est en Jackson 3, la persistance en
 * Jackson 2. Un {@code com.fasterxml.jackson.databind.JsonNode} sur un DTO d'entrée
 * est illisible par le convertisseur HTTP — il ne sait pas le construire — et toute
 * requête qui le porte répond 500. C'est arrivé sur la création de défis
 * automatiques, où le champ des paramètres était resté typé ainsi.</p>
 *
 * <p>Le compilateur ne peut pas l'attraper : les deux types sont valides, ils ne se
 * rencontrent qu'à l'exécution. Ce test lit donc les sources.</p>
 */
class HttpDtoJacksonBoundaryTest {

    private static final String JACKSON_2_NODE = "com.fasterxml.jackson.databind.JsonNode";

    @Test
    void noApiDtoCarriesAJackson2Tree() throws IOException {
        Path sources = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(sources)) {
            List<String> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/dto/"))
                    .filter(HttpDtoJacksonBoundaryTest::importsJackson2Node)
                    .map(sources::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();

            assertThat(offenders)
                    .as("ces DTO traversent la frontière HTTP : leur JSON libre doit être une Map, "
                            + "convertie par JsonPayloads une fois la frontière passée")
                    .isEmpty();
        }
    }

    private static boolean importsJackson2Node(Path path) {
        try {
            return Files.readString(path).contains(JACKSON_2_NODE);
        } catch (IOException unreadable) {
            return false;
        }
    }
}
