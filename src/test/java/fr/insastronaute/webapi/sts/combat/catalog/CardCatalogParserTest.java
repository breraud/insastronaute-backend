package fr.insastronaute.webapi.sts.combat.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CardCatalogParserTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final CardCatalogParser parser = new CardCatalogParser();

    /**
     * Le drapeau « campagne seulement » se lit sur la carte comme multiplayerExclusive se lit :
     * le catalogue l'accepte sans le porter, et c'est la construction de deck qui s'en sert. Sans
     * cette entrée dans la liste blanche, une carte qui le porte ne se chargerait pas du tout.
     */
    @Test
    void acceptsThePveOnlyFlagOnACard() throws Exception {
        CardCatalog catalog = parse(cardWith("\"pveOnly\":true"));

        assertThat(catalog.cards().keySet()).containsExactly("strike");
    }

    @Test
    void parsesSupportedCardsAndPreservesSourceOrder() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"cards-2026-07","cards":[
                  {"id":"strike","cost":1,"xCost":false,"targetingMode":"Enemy",
                   "effects":[{"type":"Damage","value":6},{"type":"Armor","value":2}]},
                  {"id":"battery","cost":0,"xCost":false,"targetingMode":"Player",
                   "effects":[{"type":"GainEnergy","value":1},{"type":"Draw","value":1}]}
                ]}
                """);

        assertThat(catalog.dataVersion()).isEqualTo("cards-2026-07");
        assertThat(catalog.cards().keySet()).containsExactly("strike", "battery");
        assertThat(catalog.require("strike")).isEqualTo(new CardDefinition(
                "strike",
                1,
                TargetingMode.SINGLE_ENEMY,
                List.of(
                        new EffectDefinition(EffectType.DAMAGE, 6),
                        new EffectDefinition(EffectType.ARMOR, 2)
                )
        ));
        assertThat(catalog.require("battery")).isEqualTo(new CardDefinition(
                "battery",
                0,
                TargetingMode.SELF,
                List.of(
                        new EffectDefinition(EffectType.GAIN_ENERGY, 1),
                        new EffectDefinition(EffectType.DRAW, 1)
                )
        ));
    }

    @Test
    void preservesPerEffectSelfTargetingOnAnEnemyCard() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"vision_en_coupe","cost":1,"targetingMode":"Enemy",
                  "effects":[
                    {"type":"Damage","value":11,"targetSelf":false},
                    {"type":"Armor","value":11,"targetSelf":true}
                  ]
                }]}
                """);

        assertThat(catalog.require("vision_en_coupe").effects())
                .containsExactly(
                        new EffectDefinition(EffectType.DAMAGE, 11, false),
                        new EffectDefinition(EffectType.ARMOR, 11, true));
    }

    @Test
    void acceptsNegativeStatusValuesButStillRejectsNegativeDamage() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"vibration","cost":0,"targetingMode":"RandomEnemy",
                  "effects":[{"type":"Status","value":-1,"statusType":"Speed"}]
                }]}
                """);

        assertThat(catalog.require("vibration").effects())
                .containsExactly(new EffectDefinition(EffectType.STATUS, -1, false, "Speed", 0, null, null));
        assertInvalid("""
                {"dataVersion":"v1","cards":[{
                  "id":"invalid","cost":0,"targetingMode":"Enemy",
                  "effects":[{"type":"Damage","value":-1}]
                }]}
                """, "value", "non-negative");
    }

    @Test
    void acceptsMinusOneAsDispelAllSentinel() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"cleanse","cost":0,"targetingMode":"Player",
                  "effects":[{"type":"DispelDebuff","value":-1,"targetSelf":true}]
                }]}
                """);

        assertThat(catalog.require("cleanse").effects())
                .containsExactly(new EffectDefinition(EffectType.DISPEL_DEBUFF, -1, true));
    }

    @Test
    void producesAnImmutableCatalogAndImmutableEffectLists() throws Exception {
        CardCatalog catalog = parse(validCatalog());

        assertThatThrownBy(() -> catalog.cards().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> catalog.require("strike").effects().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reportsAnExplicitErrorWhenRequiredCardIsAbsent() throws Exception {
        CardCatalog catalog = parse(validCatalog());

        assertThatThrownBy(() -> catalog.require("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing")
                .hasMessageContaining("card");
    }

    @Test
    void acceptsSupportedOrMissingCardTypes() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[
                  {"id":"strike","cost":1,"type":"Attaque","targetingMode":"Enemy",
                   "effects":[{"type":"Damage","value":6}]},
                  {"id":"guard","cost":1,"type":"Compétence","targetingMode":"Player",
                   "effects":[{"type":"Armor","value":5}]},
                  {"id":"minimal","cost":0,"targetingMode":"Player",
                   "effects":[{"type":"Draw","value":1}]}
                ]}
                """);

        assertThat(catalog.cards().keySet()).containsExactly("strike", "guard", "minimal");
    }

    @Test
    void preservesFiltersForAddRandomCardEffects() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"generation_spontanee","cost":1,"type":"Attaque","targetingMode":"Enemy",
                  "effects":[{"type":"AddRandomCard","value":1,"targetSelf":true,
                              "cardSelectionSource":"Hand","cardFilterTags":["Atom"]}]
                }]}
                """);

        assertThat(catalog.require("generation_spontanee").effects().getFirst().cardFilterTags())
                .containsExactly(CardFilterTag.ATOM);
    }

    @Test
    void parsesAuthoritativeMultiTargetModes() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[
                  {"id":"all-enemies","cost":1,"targetingMode":"AllEnemies","effects":[{"type":"Damage","value":2}]},
                  {"id":"all-characters","cost":1,"targetingMode":"AllCharacters","effects":[{"type":"Damage","value":2}]},
                  {"id":"random-enemy","cost":1,"targetingMode":"RandomEnemy","effects":[{"type":"Damage","value":2}]}
                ]}
                """);

        assertThat(catalog.require("all-enemies").targetingMode()).isEqualTo(TargetingMode.ALL_ENEMIES);
        assertThat(catalog.require("all-characters").targetingMode()).isEqualTo(TargetingMode.ALL_CHARACTERS);
        assertThat(catalog.require("random-enemy").targetingMode()).isEqualTo(TargetingMode.RANDOM_ENEMY);
    }

    @Test
    void rejectsDuplicateCardIdsWithFieldAndCardContext() {
        assertInvalid("""
                {"dataVersion":"v1","cards":[
                  {"id":"strike","cost":1,"targetingMode":"Enemy","effects":[{"type":"Damage","value":6}]},
                  {"id":"strike","cost":0,"targetingMode":"Player","effects":[{"type":"Draw","value":1}]}
                ]}
                """, "cards[1]", "strike", "id");
    }

    @Test
    void rejectsMissingBlankOrNonTextualDataVersions() {
        assertInvalid("{\"cards\":[]}", "dataVersion");
        assertInvalid("{\"dataVersion\":\" \",\"cards\":[]}", "dataVersion");
        assertInvalid("{\"dataVersion\":1,\"cards\":[]}", "dataVersion");
    }

    @Test
    void rejectsUnknownRootFields() {
        assertInvalid(
                "{\"dataVersion\":\"v1\",\"cards\":[],\"serverFlag\":true}",
                "root.serverFlag",
                "unknown");
    }

    @Test
    void catalogRejectsMapKeysThatDifferFromDefinitionIds() {
        CardDefinition strike = new CardDefinition(
                "strike",
                1,
                TargetingMode.SINGLE_ENEMY,
                List.of(new EffectDefinition(EffectType.DAMAGE, 6)));

        assertThatThrownBy(() -> new CardCatalog("v1", java.util.Map.of("alias", strike)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alias")
                .hasMessageContaining("strike");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCatalogs")
    void rejectsMalformedOrUnsupportedCatalogData(
            String description,
            String json,
            String[] expectedMessageParts
    ) {
        assertInvalid(json, expectedMessageParts);
    }

    @Test
    void ignoresVisualAndNeutralLegacyMetadata() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"strike","cost":1,"xCost":false,"targetingMode":"Enemy",
                  "cardName":"Strike","iconId":"strike.png","rarity":"Base",
                  "favoredCharacter":"hero","animationSpeed":1.0,"startingCount":1,
                  "tags":[],"modifiers":[],
                  "effects":[{
                    "type":"Damage","value":6,
                    "animationType":"Slash","description":"Deal damage","index":7,
                    "statusType":"Burn","targetSelf":false,
                    "cardSelectionSource":"Hand","cardSelectionEffect":"None",
                    "cardFilterTags":[]
                  }]
                }]}
                """);

        assertThat(catalog.require("strike").effects())
                .containsExactly(new EffectDefinition(
                        EffectType.DAMAGE, 6, false, "Burn", 0, "Deal damage", null, null, null,
                        List.of(), false, false, null, 0, AnimationType.SLASH));
    }

    /**
     * A blow's animation is no longer decoration.
     *
     * <p>It was read and discarded for as long as nothing asked what a blow looked like. ITI does:
     * it names the copies it tears off the other side after their effects, and fire and ice are
     * the same Damage effect until the animation separates them.
     */
    @Test
    void keepsTheAnimationOfABlow() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"brasier","cost":1,"type":"Attaque","targetingMode":"Enemy",
                  "effects":[{"type":"Damage","value":6,"animationType":"Fire"}]
                }]}
                """);

        assertThat(catalog.require("brasier").effects().getFirst().animationType())
                .isEqualTo(AnimationType.FIRE);
    }

    /** An effect that does not strike has no flavour to keep, whatever Unity wrote on it. */
    @Test
    void leavesANonStrikingEffectPlain() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"garde","cost":1,"type":"Compétence","targetingMode":"Player",
                  "effects":[{"type":"Armor","value":5,"animationType":"Fire","targetSelf":true}]
                }]}
                """);

        assertThat(catalog.require("garde").effects().getFirst().animationType())
                .isEqualTo(AnimationType.DEFAULT);
    }

    @Test
    void refusesAnAnimationItDoesNotKnow() {
        assertInvalid("""
                {"dataVersion":"v1","cards":[{
                  "id":"brasier","cost":1,"type":"Attaque","targetingMode":"Enemy",
                  "effects":[{"type":"Damage","value":6,"animationType":"Sparkles"}]
                }]}
                """, new String[] {"animationType", "Sparkles"});
    }

    @Test
    void parsesACardWhoseCostIsWhateverTheCasterHolds() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"combustion","cost":0,"xCost":true,"type":"Attaque","targetingMode":"Enemy",
                  "effects":[{"type":"Damage","value":5}]
                }]}
                """);

        CardDefinition combustion = catalog.require("combustion");
        assertThat(combustion.xCost()).isTrue();
        assertThat(combustion.costFor(4)).isEqualTo(4);
        assertThat(combustion.replaysFor(4)).isEqualTo(4);
    }

    @Test
    void parsesTheGateOnAConditionalEffect() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"opportunisme","cost":1,"type":"Attaque","targetingMode":"Enemy","effects":[{
                    "type":"Damage","value":14,
                    "conditional":true,"conditionType":"TargetHasStatus","conditionValue":"Vuln"
                  }]
                }]}
                """);

        EffectDefinition effect = catalog.require("opportunisme").effects().getFirst();
        assertThat(effect.isConditional()).isTrue();
        assertThat(effect.condition().type()).isEqualTo(ConditionType.TARGET_HAS_STATUS);
        assertThat(effect.condition().value()).isEqualTo("Vuln");
    }

    @Test
    void ignoresAConditionTypeOnAnEffectThatIsNotConditional() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"frappe","cost":1,"type":"Attaque","targetingMode":"Enemy","effects":[{
                    "type":"Damage","value":6,
                    "conditional":false,"conditionType":"KillingBlow","conditionValue":"3"
                  }]
                }]}
                """);

        assertThat(catalog.require("frappe").effects().getFirst().isConditional()).isFalse();
    }

    @Test
    void parsesTheEffectsThatSplashAndThatIgnoreResistance() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"onde","cost":1,"type":"Attaque","targetingMode":"Enemy","effects":[
                    {"type":"Damage","value":8},
                    {"type":"Damage","value":4,"targetOthers":true},
                    {"type":"DispelBuff","value":1,"trueEffect":true}
                  ]
                }]}
                """);

        List<EffectDefinition> effects = catalog.require("onde").effects();
        assertThat(effects.get(0).targetOthers()).isFalse();
        assertThat(effects.get(1).targetOthers()).isTrue();
        assertThat(effects.get(2).trueEffect()).isTrue();
    }

    @Test
    void acceptsACardWhoseBehaviourIsNotCarriedByEffects() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"archi","cardName":"Archi","cost":1,"type":"Attaque","targetingMode":"Enemy",
                  "effects":[]
                }]}
                """);

        assertThat(catalog.require("archi").effects()).isEmpty();
    }

    @Test
    void ignoresSelectionMetadataOnEffectsThatDoNotReadIt() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"strike","cost":1,"targetingMode":"Enemy","effects":[{
                    "type":"Damage","value":6,
                    "cardSelectionSource":"All","cardSelectionEffect":"Exhaust",
                    "cardFilterTags":["Atom"]
                  }]
                }]}
                """);

        EffectDefinition effect = catalog.require("strike").effects().getFirst();
        assertThat(effect.cardSelectionSource()).isNull();
        assertThat(effect.cardSelectionAction()).isNull();
        assertThat(effect.cardFilterTags()).isEmpty();
    }

    @Test
    void resolvesACardByItsAuthoredNameAsWellAsItsId() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"amalgame","cardName":"Amalgame","cost":2,"targetingMode":"Player",
                  "effects":[{"type":"Draw","value":1,"targetSelf":true}]
                }]}
                """);

        assertThat(catalog.require("Amalgame")).isSameAs(catalog.require("amalgame"));
        assertThat(catalog.find("inconnue")).isNull();
    }

    @Test
    void parsesEveryUnityCardFamilyAndTreatsAMissingOneAsUntyped() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[
                  {"id":"strike","cost":1,"type":"Attaque","targetingMode":"Enemy",
                   "effects":[{"type":"Damage","value":6}]},
                  {"id":"guard","cost":1,"type":"Compétence","targetingMode":"Player",
                   "effects":[{"type":"Armor","value":5,"targetSelf":true}]},
                  {"id":"surchauffe","cost":1,"type":"Pouvoir","targetingMode":"None",
                   "effects":[{"type":"Status","value":2,"targetSelf":true,"statusType":"Strength","duration":-1}]},
                  {"id":"rouille","cost":0,"type":"Rien","targetingMode":"None",
                   "effects":[{"type":"LoseHP","value":2,"targetSelf":true}]},
                  {"id":"legacy","cost":0,"targetingMode":"Player",
                   "effects":[{"type":"Draw","value":1,"targetSelf":true}]}
                ]}
                """);

        assertThat(catalog.require("strike").type()).isEqualTo(CardType.ATTACK);
        assertThat(catalog.require("guard").type()).isEqualTo(CardType.SKILL);
        assertThat(catalog.require("surchauffe").type()).isEqualTo(CardType.POWER);
        assertThat(catalog.require("rouille").type()).isEqualTo(CardType.NONE);
        assertThat(catalog.require("legacy").type()).isEqualTo(CardType.NONE);
    }

    @Test
    void parsesCardsThatDesignateNoTarget() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"surchauffe","cost":1,"type":"Pouvoir","targetingMode":"None",
                  "effects":[{"type":"Status","value":2,"targetSelf":true,"statusType":"Strength"}]
                }]}
                """);

        assertThat(catalog.require("surchauffe").targetingMode()).isEqualTo(TargetingMode.NONE);
    }

    @Test
    void parsesAuthoredTagsBecauseCardSelectionFiltersReadThem() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"reserve","cost":1,"type":"Compétence","targetingMode":"Player",
                  "tags":["Retain","Atom"],
                  "effects":[{"type":"Armor","value":7,"targetSelf":true}]
                }]}
                """);

        assertThat(catalog.require("reserve").tags())
                .containsExactlyInAnyOrder(CardTag.RETAIN, CardTag.ATOM);
        assertThat(catalog.require("reserve").hasTag(CardTag.MOLECULE)).isFalse();
    }

    @Test
    void preservesCardSelectionSemanticsFromUnityCatalog() throws Exception {
        CardCatalog catalog = parse("""
                {"dataVersion":"v1","cards":[{
                  "id":"contrepoids","cost":1,"targetingMode":"Player","effects":[{
                    "type":"CardSelection","value":1,"targetSelf":true,
                    "cardSelectionSource":"Hand","cardSelectionEffect":"Exhaust",
                    "cardFilterTags":[]
                  }]
                }]}
                """);

        EffectDefinition effect = catalog.require("contrepoids").effects().getFirst();
        assertThat(effect.cardSelectionSource()).isEqualTo(CardSelectionSource.HAND);
        assertThat(effect.cardSelectionAction()).isEqualTo(CardSelectionAction.EXHAUST);
        assertThat(effect.cardFilterTags()).isEmpty();
    }

    private static java.util.stream.Stream<Arguments> invalidCatalogs() {
        return java.util.stream.Stream.of(
                invalid("null root", "null", "root"),
                invalid("missing cards", "{\"dataVersion\":\"v1\"}", "cards"),
                invalid("cards is not an array",
                        "{\"dataVersion\":\"v1\",\"cards\":{}}", "cards", "array"),
                invalid("card is not an object",
                        "{\"dataVersion\":\"v1\",\"cards\":[1]}", "cards[0]", "object"),
                invalid("missing id", """
                        {"dataVersion":"v1","cards":[{"cost":1,"targetingMode":"Enemy","effects":[{"type":"Damage","value":6}]}]}
                        """, "cards[0]", "id"),
                invalid("blank id", """
                        {"dataVersion":"v1","cards":[{"id":" ","cost":1,"targetingMode":"Enemy","effects":[{"type":"Damage","value":6}]}]}
                        """, "cards[0]", "id"),
                invalid("missing cost", """
                        {"dataVersion":"v1","cards":[{"id":"strike","targetingMode":"Enemy","effects":[{"type":"Damage","value":6}]}]}
                        """, "cards[0]", "strike", "cost"),
                invalid("non integral cost", """
                        {"dataVersion":"v1","cards":[{"id":"strike","cost":1.5,"targetingMode":"Enemy","effects":[{"type":"Damage","value":6}]}]}
                        """, "cards[0]", "strike", "cost"),
                invalid("negative cost", """
                        {"dataVersion":"v1","cards":[{"id":"strike","cost":-1,"targetingMode":"Enemy","effects":[{"type":"Damage","value":6}]}]}
                        """, "cards[0]", "strike", "cost"),
                invalid("missing targeting", """
                        {"dataVersion":"v1","cards":[{"id":"strike","cost":1,"effects":[{"type":"Damage","value":6}]}]}
                        """, "cards[0]", "strike", "targetingMode"),
                invalid("effects is not an array", """
                        {"dataVersion":"v1","cards":[{"id":"strike","cost":1,"targetingMode":"Enemy","effects":{}}]}
                        """, "cards[0]", "strike", "effects", "array"),
                invalid("effect is not an object", """
                        {"dataVersion":"v1","cards":[{"id":"strike","cost":1,"targetingMode":"Enemy","effects":[1]}]}
                        """, "cards[0]", "strike", "effects[0]", "object"),
                invalid("missing effect value", """
                        {"dataVersion":"v1","cards":[{"id":"strike","cost":1,"targetingMode":"Enemy","effects":[{"type":"Damage"}]}]}
                        """, "cards[0]", "strike", "effects[0]", "value"),
                invalid("non integral effect value", """
                        {"dataVersion":"v1","cards":[{"id":"strike","cost":1,"targetingMode":"Enemy","effects":[{"type":"Damage","value":"6"}]}]}
                        """, "cards[0]", "strike", "effects[0]", "value"),
                invalid("negative effect value", """
                        {"dataVersion":"v1","cards":[{"id":"strike","cost":1,"targetingMode":"Enemy","effects":[{"type":"Damage","value":-1}]}]}
                        """, "cards[0]", "strike", "effects[0]", "value"),
                invalid("missing effect type", """
                        {"dataVersion":"v1","cards":[{"id":"strike","cost":1,"targetingMode":"Enemy","effects":[{"value":1}]}]}
                        """, "cards[0]", "strike", "effects[0]", "type"),

                invalid("non boolean x cost", """
                        {"dataVersion":"v1","cards":[{"id":"strike","cost":1,"xCost":"false","targetingMode":"Enemy","effects":[{"type":"Damage","value":6}]}]}
                        """, "cards[0]", "strike", "xCost"),
                invalid("unknown card type", cardWith("\"type\":\"Malédiction\""),
                        "cards[0]", "strike", "type", "Malédiction"),
                invalid("non textual card type", cardWith("\"type\":1"),
                        "cards[0]", "strike", "type", "string"),
                invalid("unknown card field", cardWith("\"serverSemanticFlag\":true"),
                        "cards[0]", "strike", "serverSemanticFlag"),
                invalid("other targeting", targetedCard("AnythingElse"), "cards[0]", "strike", "targetingMode", "AnythingElse"),
                invalid("nonempty card modifiers", """
                        {"dataVersion":"v1","cards":[{"id":"strike","cost":1,"targetingMode":"Enemy","modifiers":[{"type":"Double"}],
                         "effects":[{"type":"Damage","value":6}]}]}
                        """, "cards[0]", "strike", "modifiers"),
                invalid("object card modifiers", cardWith("\"modifiers\":{}"),
                        "cards[0]", "strike", "modifiers", "array"),
                invalid("string card modifiers", cardWith("\"modifiers\":\"Double\""),
                        "cards[0]", "strike", "modifiers", "array"),
                invalid("boolean card modifiers", cardWith("\"modifiers\":false"),
                        "cards[0]", "strike", "modifiers", "array"),
                invalid("unknown card tag", cardWith("\"tags\":[\"Sournoise\"]"),
                        "cards[0]", "strike", "tags[0]", "Sournoise"),
                invalid("object filter tags on a selection", selectionEffectWith("\"cardFilterTags\":{}"),
                        "effects[0]", "cardFilterTags", "array"),
                invalid("string filter tags on a selection", selectionEffectWith("\"cardFilterTags\":\"Attack\""),
                        "effects[0]", "cardFilterTags", "array"),
                invalid("unknown condition type",
                        effectWith("\"conditional\":true,\"conditionType\":\"SiLaLuneEstPleine\""),
                        "effects[0]", "conditionType", "SiLaLuneEstPleine"),
                invalid("unknown card selection filter",
                        selectionEffectWith("\"cardFilterTags\":[\"Sournoise\"]"),
                        "effects[0]", "cardFilterTags[0]", "Sournoise"),
                invalid("unknown card selection source",
                        selectionEffectWith("\"cardSelectionSource\":\"Deck\""),
                        "effects[0]", "cardSelectionSource", "Deck"),
                invalid("unknown card selection action",
                        selectionEffectWith("\"cardSelectionEffect\":\"Shred\""),
                        "effects[0]", "cardSelectionEffect", "Shred"),
                invalid("object card tags", cardWith("\"tags\":{}"),
                        "cards[0]", "strike", "tags", "array"),
                invalid("string card tags", cardWith("\"tags\":\"Attack\""),
                        "cards[0]", "strike", "tags", "array"),
                invalid("boolean card tags", cardWith("\"tags\":true"),
                        "cards[0]", "strike", "tags", "array"),
                invalid("effect modifiers", effectWith("\"modifiers\":[{\"type\":\"Double\"}]"),
                        "effects[0]", "modifiers"),
                invalid("unknown effect field", effectWith("\"serverSemanticFlag\":true"),
                        "effects[0]", "serverSemanticFlag"),
                invalid("non boolean target self", effectWith("\"targetSelf\":\"true\""),
                        "effects[0]", "targetSelf", "boolean")
        );
    }

    private static Arguments invalid(String description, String json, String... expectedMessageParts) {
        return Arguments.of(description, json, expectedMessageParts);
    }

    private static String targetedCard(String targetingMode) {
        return """
                {"dataVersion":"v1","cards":[{"id":"strike","cost":1,"targetingMode":"%s",
                 "effects":[{"type":"Damage","value":6}]}]}
                """.formatted(targetingMode);
    }

    private static String selectionEffectWith(String field) {
        return """
                {"dataVersion":"v1","cards":[{"id":"strike","cost":1,"targetingMode":"Player",
                 "effects":[{"type":"CardSelection","value":1,%s}]}]}
                """.formatted(field);
    }

    private static String effectWith(String field) {
        return """
                {"dataVersion":"v1","cards":[{"id":"strike","cost":1,"targetingMode":"Enemy",
                 "effects":[{"type":"Damage","value":6,%s}]}]}
                """.formatted(field);
    }

    private static String cardWith(String field) {
        return """
                {"dataVersion":"v1","cards":[{"id":"strike","cost":1,"targetingMode":"Enemy",%s,
                 "effects":[{"type":"Damage","value":6}]}]}
                """.formatted(field);
    }

    private static String validCatalog() {
        return """
                {"dataVersion":"v1","cards":[
                  {"id":"strike","cost":1,"targetingMode":"Enemy","effects":[{"type":"Damage","value":6}]},
                  {"id":"battery","cost":0,"targetingMode":"Player","effects":[{"type":"GainEnergy","value":1}]}
                ]}
                """;
    }

    private CardCatalog parse(String json) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        return parser.parse(root);
    }

    private void assertInvalid(String json, String... expectedMessageParts) {
        assertThatThrownBy(() -> parse(json))
                .isInstanceOf(CardCatalogValidationException.class)
                .satisfies(error -> assertThat(error.getMessage())
                        .contains(expectedMessageParts));
    }
}
