package fr.insastronaute.webapi.sts.combat.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CardCatalogFactoryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final CardCatalogFactory factory = new CardCatalogFactory();

    @Test
    void includesTheLandingCardRequiredByJump() throws Exception {
        CardCatalog catalog = create("Jump", "atterrissage", "Atterrissage");

        assertThat(catalog.cards()).containsKeys("starter", "atterrissage");
        assertThat(catalog.find("Atterrissage").id()).isEqualTo("atterrissage");
    }

    @Test
    void includesTheCrystalCardRequiredByCrystallize() throws Exception {
        CardCatalog catalog = create("Crystallize", "recristallisation", "Recristallisation");

        assertThat(catalog.cards()).containsKeys("starter", "recristallisation");
        assertThat(catalog.find("Recristallisation").id()).isEqualTo("recristallisation");
    }

    @Test
    void reusesVersionIndexButBuildsReachableCardsForEachCombat() throws Exception {
        var source = OBJECT_MAPPER.readTree("""
                {"dataVersion":"v1","cards":[
                  {"id":"strike","cost":1,"targetingMode":"Enemy","effects":[]},
                  {"id":"guard","cost":1,"targetingMode":"Player","effects":[]}
                ]}
                """);

        CardCatalog firstCombat = factory.create(source, "v1", java.util.List.of("strike"), "");
        CardCatalog nextCombat = factory.create(source, "v1", java.util.List.of("guard"), "");

        assertThat(firstCombat.cards()).containsKey("strike").doesNotContainKey("guard");
        assertThat(nextCombat.cards()).containsKey("guard").doesNotContainKey("strike");
    }

    @Test
        void cachesTheFullCatalogRequiredByTransformEffectsPerCharacter() throws Exception {
        var source = OBJECT_MAPPER.readTree("""
                {"dataVersion":"v-full","cards":[
                                    {"id":"transform-card","cost":1,"type":"Compétence","targetingMode":"Player",
                                     "effects":[{"type":"CardSelection","value":1,"targetSelf":true,
                                                             "cardSelectionSource":"Hand","cardSelectionEffect":"Transform",
                                                             "cardFilterTags":[]}]},
                  {"id":"other-card","cost":1,"targetingMode":"Player","effects":[]}
                ]}
                """);

                CardCatalog firstCombat = factory.create(source, "v-full", java.util.List.of("transform-card"), "ITI");
                CardCatalog nextCombat = factory.create(source, "v-full", java.util.List.of("transform-card"), "ITI");
                CardCatalog otherCharacter = factory.create(source, "v-full", java.util.List.of("transform-card"), "MECA");

                assertThat(firstCombat.cards()).containsKeys("transform-card", "other-card");
        assertThat(nextCombat).isSameAs(firstCombat);
        assertThat(otherCharacter).isNotSameAs(firstCombat);
    }

    private CardCatalog create(String statusType, String followUpId, String followUpName) throws Exception {
        return factory.create(OBJECT_MAPPER.readTree("""
                {"dataVersion":"v1","cards":[
                  {"id":"starter","cost":1,"targetingMode":"Enemy",
                   "effects":[{"type":"Status","value":1,"statusType":"%s"}]},
                  {"id":"%s","cardName":"%s","cost":0,"targetingMode":"Enemy",
                   "effects":[{"type":"Damage","value":4}]}
                ]}
                """.formatted(statusType, followUpId, followUpName)), "v1", java.util.List.of("starter"), "");
    }
}