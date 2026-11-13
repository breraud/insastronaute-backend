package fr.insastronaute.webapi.sts.combat.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.EnemyMoveParser;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce qu'il advient des enchaînements quand un coup est écarté.
 *
 * <p>Un coup vide — ni {@code cardId} ni effet — est écarté au chargement, et c'est la
 * bonne décision : il n'y a rien à jouer. Mais les {@code nextMoveIndices} désignent des
 * positions dans la liste construite, pas dans celle déclarée. Sans reprise, écarter le
 * premier coup décalait tous les suivants d'un cran : l'ennemi jouait en silence un
 * enchaînement que personne n'avait écrit.</p>
 *
 * <p>Vu en production le 11 septembre 2026 sur « Leo », dont le premier coup était arrivé
 * vide dans les données de jeu.</p>
 */
class EnemyPatternReaderDropTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record CollectedDrop(String enemyId, String field, int index) {}

    private static final class Collector implements EnemyPatternReader.Diagnostics {
        private final List<CollectedDrop> drops = new ArrayList<>();
        private int movelessEnemies;

        @Override
        public void moveDropped(String enemyId, String field, int moveIndex, String reason) {
            drops.add(new CollectedDrop(enemyId, field, moveIndex));
        }

        @Override
        public void noUsableMove(String enemyId, int declaredMoves) {
            movelessEnemies += 1;
        }
    }

    private static CardCatalog catalogOf(String... cardIds) {
        Map<String, CardDefinition> definitions = new java.util.LinkedHashMap<>();
        for (String cardId : cardIds) {
            definitions.put(cardId, new CardDefinition(
                    cardId, 0, fr.insastronaute.webapi.sts.combat.catalog.CardType.ATTACK,
                    TargetingMode.SINGLE_ENEMY, Set.of(),
                    List.of(new EffectDefinition(EffectType.DAMAGE, 5, false, null, 0, "", null))));
        }
        return new CardCatalog("v1", definitions);
    }

    private static JsonNode enemy(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /**
     * Le coup écarté disparaît, et les transitions suivent : celles qui le visaient ne
     * visent plus rien, les autres retrouvent leur cible.
     */
    @Test
    void droppingAMoveKeepsEveryTransitionPointingAtTheSameMove() {
        Collector diagnostics = new Collector();

        // Le coup 0 est vide, comme celui de Leo. Les trois autres se visent entre eux par
        // leurs indices déclarés.
        EnemyPatternReader.Pattern pattern = EnemyPatternReader.read(
                catalogOf("strike", "guard", "roar"),
                enemy("""
                        {
                          "movePattern": [
                            { "nextMoveIndices": [1] },
                            { "cardId": "strike", "nextMoveIndices": [2] },
                            { "cardId": "guard",  "nextMoveIndices": [3] },
                            { "cardId": "roar",   "nextMoveIndices": [1, 0] }
                          ]
                        }
                        """),
                "Leo", new EnemyMoveParser(), diagnostics);

        assertThat(diagnostics.drops).containsExactly(new CollectedDrop("Leo", "movePattern", 0));
        assertThat(pattern.declaredMoves()).isEqualTo(4);
        assertThat(pattern.moves()).extracting(PatternEnemyPolicy.EnemyMoveNode::cardId)
                .containsExactly("strike", "guard", "roar");

        // « strike » visait le coup déclaré 2, désormais en position 1 ; « guard » visait
        // le 3, désormais en position 2.
        assertThat(pattern.moves().get(0).nextIndices()).containsExactly(1);
        assertThat(pattern.moves().get(1).nextIndices()).containsExactly(2);
        // « roar » visait le 1 — en position 0 — et le 0, qui n'existe plus : il ne reste
        // que le premier. Le laisser pointer sur son voisin inventerait un enchaînement.
        assertThat(pattern.moves().get(2).nextIndices()).containsExactly(0);
        assertThat(diagnostics.movelessEnemies).isZero();
    }

    /** Sans coup écarté, rien ne bouge : la numérotation déclarée est déjà la bonne. */
    @Test
    void anIntactPatternIsLeftAlone() {
        EnemyPatternReader.Pattern pattern = EnemyPatternReader.read(
                catalogOf("strike", "guard"),
                enemy("""
                        {
                          "movePattern": [
                            { "cardId": "strike", "nextMoveIndices": [1] },
                            { "cardId": "guard",  "nextMoveIndices": [0] }
                          ]
                        }
                        """),
                "Leo", new EnemyMoveParser(), new Collector());

        assertThat(pattern.moves().get(0).nextIndices()).containsExactly(1);
        assertThat(pattern.moves().get(1).nextIndices()).containsExactly(0);
    }
}
