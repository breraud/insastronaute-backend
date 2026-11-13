package fr.insastronaute.webapi.common.adminaudit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CardService.grant journalise en son sein, et cela n'est juste que tant qu'il
 * n'a qu'un seul appelant : AdminCardController.
 *
 * <p>Toute la conception du registre repose sur la regle "on journalise a la
 * frontiere administration". grant y deroge par exception, parce que son unique
 * appelant est un controleur d'administration. Le jour ou un chemin joueur
 * appellerait grant, il ecrirait des lignes d'administration sur un geste de
 * joueur - et rien dans la suite ne tomberait : {@link AdminBoundaryTest} et
 * {@link PvpRewardBoundaryTest} gardent grantVirtualCardsByName et
 * addVirtualCards, pas grant.</p>
 *
 * <p>Ce test lit les sources plutot que d'observer un comportement, faute de
 * pouvoir observer un appelant qui n'existe pas encore. Meme motif que
 * {@link AdminActionTypeMigrationTest}.</p>
 */
class CardServiceGrantCallersTest {

    private static final Path SOURCES = Paths.get("src/main/java");

    private static final String SERVICE = "CardService";

    /**
     * Le seul appelant legitime.
     *
     * <p>Si tu ajoutes un appelant admin ici, ajoute aussi le test qui prouve
     * qu'il journalise. Si tu ajoutes un appelant joueur, ne l'ajoute pas ici :
     * sors la journalisation de grant vers son appelant admin, comme partout
     * ailleurs.</p>
     */
    private static final Set<String> EXPECTED_CALLERS = Set.of("AdminCardController.java");

    /**
     * Le recepteur, pas le nom nu.
     *
     * <p>{@code RelicActions.grant(...)} vit dans les paquets STS et n'a rien a
     * voir : un motif sur "grant(" seul le ramasserait. On ne regarde donc que
     * les fichiers qui citent CardService, et dedans, que les appels sur une
     * variable dont le type declare est CardService.</p>
     */
    private static final Pattern CARD_SERVICE_BINDING =
            Pattern.compile("\\b" + SERVICE + "\\s+([a-z][A-Za-z0-9_]*)\\s*[;,)=]");

    @Test
    void onlyTheAdminControllerCallsGrant() throws IOException {
        Set<String> callers = new LinkedHashSet<>();
        for (Path source : javaSources()) {
            String body = Files.readString(source, StandardCharsets.UTF_8);
            if (!body.contains(SERVICE)) {
                continue;
            }
            for (String receiver : cardServiceReceivers(body)) {
                if (body.contains(receiver + ".grant(")) {
                    callers.add(source.getFileName().toString());
                }
            }
        }

        assertThat(callers)
                .as("les appelants de %s.grant doivent rester exactement %s ; "
                        + "grant journalise en son sein, ce qui n'est vrai que pour un appelant admin",
                        SERVICE, EXPECTED_CALLERS)
                .containsExactlyInAnyOrderElementsOf(EXPECTED_CALLERS);
    }

    /**
     * Le motif doit pouvoir tomber : s'il ne reconnait plus le seul appelant
     * connu, il ne reconnaitrait pas non plus le prochain, et le test passerait
     * vert en ne regardant rien.
     */
    @Test
    void theMatcherRecognizesACallOnACardServiceField() {
        String source = """
                class Whatever {
                    private final CardService cardService;
                    void go() {
                        cardService.grant(id, type, request, sub);
                    }
                }
                """;

        Set<String> receivers = cardServiceReceivers(source);

        assertThat(receivers).contains("cardService");
        assertThat(receivers.stream().anyMatch(receiver -> source.contains(receiver + ".grant("))).isTrue();
    }

    /** Et il ne doit pas confondre l'homonyme des paquets STS. */
    @Test
    void theMatcherIgnoresTheUnrelatedRelicHomonym() {
        String source = """
                class SpeedCadenceRelicHandler {
                    RelicEffect apply() {
                        return RelicActions.grant(state, Stat.SPEED, 1);
                    }
                }
                """;

        assertThat(cardServiceReceivers(source)).isEmpty();
    }

    private Set<String> cardServiceReceivers(String body) {
        Set<String> receivers = new LinkedHashSet<>();
        Matcher binding = CARD_SERVICE_BINDING.matcher(body);
        while (binding.find()) {
            receivers.add(binding.group(1));
        }
        return receivers;
    }

    private List<Path> javaSources() throws IOException {
        assertThat(Files.isDirectory(SOURCES))
                .as("les sources %s doivent etre lisibles depuis le repertoire de travail des tests",
                        SOURCES.toAbsolutePath())
                .isTrue();
        try (Stream<Path> tree = Files.walk(SOURCES)) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }
}
