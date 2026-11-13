package fr.insastronaute.webapi.sts.multiplayer.combat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le barème lu depuis {@code gameplay/pvp_rating.json}.
 *
 * <p>Ce qui se vérifie ici n'est pas l'arithmétique — {@code StsPvpRatingTest} s'en charge — mais
 * la promesse faite aux versions de données publiées avant ce fichier : elles doivent continuer à
 * régler leurs duels exactement comme avant.
 */
class StsPvpRatingRulesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void anAbsentFileKeepsTheOriginalScale() {
        assertThat(StsPvpRatingRules.from(null)).isEqualTo(StsPvpRatingRules.DEFAULT);
        assertThat(StsPvpRatingRules.from(objectMapper.createObjectNode()))
                .isEqualTo(StsPvpRatingRules.DEFAULT);
    }

    @Test
    void aPartialFileOnlyOverridesWhatItNames() throws Exception {
        StsPvpRatingRules rules = StsPvpRatingRules.from(
                objectMapper.readTree("{\"tokens\":{\"winBase\":25}}"));

        assertThat(rules.winTokensBase()).isEqualTo(25L);
        assertThat(rules.kFactor()).isEqualTo(StsPvpRatingRules.DEFAULT.kFactor());
        assertThat(rules.lossTokens()).isEqualTo(StsPvpRatingRules.DEFAULT.lossTokens());
    }

    @Test
    void everyKnobIsReadable() throws Exception {
        StsPvpRatingRules rules = StsPvpRatingRules.from(objectMapper.readTree("""
                {
                  "elo": { "default": 1200, "kFactor": 16, "scale": 200, "minimumMove": 2 },
                  "tokens": {
                    "winBase": 5, "winUpsetBonus": 40, "loss": 8,
                    "perEloPoint": 0.1, "eloBaseline": 1200, "maxWin": 500
                  },
                  "combat": { "playerHpMultiplier": 1.5 }
                }
                """));

        assertThat(rules).isEqualTo(new StsPvpRatingRules(
                1200, 16, 200d, 2, 5L, 40L, 8L, 0.1d, 1200, 500L, 1.5d));
    }

    /** Un zéro glissé dans le kFactor rendrait le classement immobile : il est refusé. */
    @Test
    void anImpossibleKFactorFallsBackRatherThanFreezingTheLadder() throws Exception {
        StsPvpRatingRules rules = StsPvpRatingRules.from(
                objectMapper.readTree("{\"elo\":{\"kFactor\":0,\"scale\":0}}"));

        assertThat(rules.kFactor()).isEqualTo(StsPvpRatingRules.DEFAULT.kFactor());
        assertThat(rules.eloScale()).isEqualTo(StsPvpRatingRules.DEFAULT.eloScale());
    }
}
