package fr.insastronaute.webapi.sts.combat.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CardCatalogTest {

    @Test
    void resolvesCardsByEitherTheirAuthoredNameOrId() {
        CardDefinition recristallisation = new CardDefinition(
                "recristallisation", "Recristallisation", "", 0, CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, java.util.Set.of(), java.util.List.of());
        CardDefinition roquette = new CardDefinition(
                "roquette", "Roquette", "", 0, CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, java.util.Set.of(), java.util.List.of());
        CardCatalog catalog = new CardCatalog("test", Map.of(
                recristallisation.id(), recristallisation,
                roquette.id(), roquette));

        assertThat(catalog.find("Recristallisation")).isSameAs(recristallisation);
        assertThat(catalog.find("recristallisation")).isSameAs(recristallisation);
        assertThat(catalog.find("Roquette")).isSameAs(roquette);
        assertThat(catalog.find("roquette")).isSameAs(roquette);
    }
}