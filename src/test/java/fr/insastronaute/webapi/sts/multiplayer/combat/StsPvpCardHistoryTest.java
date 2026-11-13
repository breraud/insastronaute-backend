package fr.insastronaute.webapi.sts.multiplayer.combat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import fr.insastronaute.webapi.sts.combat.event.CardDrawn;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.TurnEnded;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'historique partage des cartes jouees d'un duel.
 *
 * <p>Ce qui est verifie ici est ce sur quoi le reste s'appuie : que seules les cartes jouees y
 * entrent, que rien n'est perdu ni reordonne, et que la liste reste bornee.
 */
class StsPvpCardHistoryTest {

    private static final UUID COMBAT = UUID.fromString("0e5f9a2c-0000-4000-8000-000000000001");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void keepsThePlayedCardsInTheOrderTheyWerePlayed() {
        ArrayNode history = StsPvpCardHistory.append(objectMapper, null, List.of(
                played(1, "player-a", "strike"),
                played(2, "player-b", "defend")));

        assertThat(StsPvpCardHistory.read(history))
                .extracting(StsPvpCardHistory.Entry::definitionId)
                .containsExactly("strike", "defend");
    }

    /**
     * Un evenement qui n'est pas une carte jouee n'a rien a faire dans un historique des cartes
     * jouees — et surtout pas {@link CardDrawn}, qui nomme une carte d'une pile que l'adversaire
     * n'a pas le droit de voir.
     */
    @Test
    void ignoresEverythingThatIsNotACardBeingPlayed() {
        ArrayNode history = StsPvpCardHistory.append(objectMapper, null, List.of(
                new CardDrawn(COMBAT, 1, "player-a", "card-9", "secret", 4),
                new TurnEnded(COMBAT, 2, "player-a", 0L, 25L),
                played(3, "player-a", "strike")));

        assertThat(StsPvpCardHistory.read(history))
                .extracting(StsPvpCardHistory.Entry::definitionId)
                .containsExactly("strike");
    }

    @Test
    void appendsToWhatTheBattleAlreadyHeld() {
        ArrayNode first = StsPvpCardHistory.append(objectMapper, null, List.of(played(1, "player-a", "strike")));
        ArrayNode second = StsPvpCardHistory.append(objectMapper, first, List.of(played(2, "player-b", "defend")));

        assertThat(StsPvpCardHistory.read(second))
                .extracting(StsPvpCardHistory.Entry::definitionId)
                .containsExactly("strike", "defend");
    }

    /**
     * L'historique est relu et reecrit a chaque commande : sa taille est un cout par tour, pas
     * seulement un cout de stockage. Ce sont les dernieres cartes qui restent, parce qu'un
     * historique sert a relire ce qui vient de se passer.
     */
    @Test
    void keepsOnlyTheMostRecentEntriesOnceItIsFull() {
        List<CombatEvent> tooMany = new ArrayList<>();
        for (int index = 0; index < StsPvpCardHistory.MAX_ENTRIES + 10; index++) {
            tooMany.add(played(index + 1, "player-a", "card-" + index));
        }

        List<StsPvpCardHistory.Entry> entries =
                StsPvpCardHistory.read(StsPvpCardHistory.append(objectMapper, null, tooMany));

        assertThat(entries).hasSize(StsPvpCardHistory.MAX_ENTRIES);
        assertThat(entries.get(0).definitionId()).isEqualTo("card-10");
        assertThat(entries.get(entries.size() - 1).definitionId())
                .isEqualTo("card-" + (StsPvpCardHistory.MAX_ENTRIES + 9));
    }

    /**
     * Hibernate ne voit pas toujours comme sale une {@code JsonNode} modifiee sur place, et un
     * ajout perdu au commit ne se signale nulle part. Le tableau rendu doit donc etre un autre
     * que celui qu'on lui a passe.
     */
    @Test
    void neverWritesIntoTheHistoryItWasGiven() {
        ArrayNode existing = StsPvpCardHistory.append(objectMapper, null, List.of(played(1, "player-a", "strike")));

        StsPvpCardHistory.append(objectMapper, existing, List.of(played(2, "player-b", "defend")));

        assertThat(existing).hasSize(1);
    }

    @Test
    void readsAnAbsentOrUnreadableHistoryAsEmpty() {
        assertThat(StsPvpCardHistory.read(null)).isEmpty();
        assertThat(StsPvpCardHistory.read(objectMapper.createObjectNode())).isEmpty();
    }

    /** Une entree qui ne designe aucune carte affichable est sautee plutot que rendue a moitie. */
    @Test
    void skipsAnEntryThatNamesNoCard() {
        ArrayNode stored = objectMapper.createArrayNode();
        stored.addObject().put("actorId", "player-a").put("definitionId", "");
        stored.addObject().put("actorId", "").put("definitionId", "strike");
        stored.addObject().put("actorId", "player-a").put("definitionId", "defend");

        assertThat(StsPvpCardHistory.read(stored))
                .extracting(StsPvpCardHistory.Entry::definitionId)
                .containsExactly("defend");
    }

    @Test
    void carriesWhatTheClientNeedsToShowTheCard() {
        JsonNode history = StsPvpCardHistory.append(objectMapper, null, List.of(
                new CardPlayed(COMBAT, 7, "player-a", "card-3", "strike", List.of("player-b"))));

        StsPvpCardHistory.Entry entry = StsPvpCardHistory.read(history).get(0);
        assertThat(entry.revision()).isEqualTo(7L);
        assertThat(entry.actorId()).isEqualTo("player-a");
        assertThat(entry.cardInstanceId()).isEqualTo("card-3");
        assertThat(entry.definitionId()).isEqualTo("strike");
        assertThat(entry.targetIds()).containsExactly("player-b");
    }

    private static CardPlayed played(long revision, String actorId, String definitionId) {
        return new CardPlayed(COMBAT, revision, actorId, "instance-" + revision, definitionId, List.of());
    }
}
