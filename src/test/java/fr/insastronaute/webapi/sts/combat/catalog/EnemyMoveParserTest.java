package fr.insastronaute.webapi.sts.combat.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnemyMoveParserTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EnemyMoveParser parser = new EnemyMoveParser();
    private final CardCatalog catalog = new CardCatalog("v1", Map.of(
            "frappe", new CardDefinition("frappe", 1, TargetingMode.SINGLE_ENEMY,
                    List.of(new EffectDefinition(EffectType.DAMAGE, 6)))));

    @Test
    void keepsAMixedMoveAimedAtTheOpponentWhileArmouringItsCaster() throws Exception {
        CardDefinition move = parse("""
                {"effects":[
                  {"type":"Damage","value":9,"targetSelf":false},
                  {"type":"Armor","value":5,"targetSelf":true}
                ]}
                """);

        assertThat(move.targetingMode()).isEqualTo(TargetingMode.SINGLE_ENEMY);
        assertThat(move.effects()).containsExactly(
                new EffectDefinition(EffectType.DAMAGE, 9, false, null, 0, null, null),
                new EffectDefinition(EffectType.ARMOR, 5, true, null, 0, null, null));
    }

    @Test
    void needsNoTargetWhenEveryEffectIsAimedAtTheCaster() throws Exception {
        CardDefinition move = parse("""
                {"effects":[
                  {"type":"Armor","value":6,"targetSelf":true},
                  {"type":"Heal","value":3,"targetSelf":true}
                ]}
                """);

        assertThat(move.targetingMode()).isEqualTo(TargetingMode.SELF);
    }

    @Test
    void acceptsTheSameEffectVocabularyAsACard() throws Exception {
        CardDefinition move = parse("""
                {"effects":[
                  {"type":"LoseHP","value":3,"targetSelf":true},
                  {"type":"Break","value":1,"targetSelf":false},
                  {"type":"ExtendStatuses","value":1,"targetSelf":true}
                ]}
                """);

        assertThat(move.effects()).extracting(EffectDefinition::type).containsExactly(
                EffectType.LOSE_HP, EffectType.BREAK, EffectType.EXTEND_STATUSES);
    }

    @Test
    void keepsTheCardSelectionSourceActionAndFiltersOfAnInlineMove() throws Exception {
        CardDefinition move = parse("""
                {"effects":[{
                  "type":"CardSelection","value":2,"targetSelf":false,
                  "cardSelectionSource":"Hand","cardSelectionEffect":"Discard",
                  "cardFilterTags":["Skill"]
                }]}
                """);

        EffectDefinition selection = move.effects().getFirst();
        assertThat(selection.cardSelectionSource()).isEqualTo(CardSelectionSource.HAND);
        assertThat(selection.cardSelectionAction()).isEqualTo(CardSelectionAction.DISCARD);
        assertThat(selection.cardFilterTags()).containsExactly(CardFilterTag.SKILL);
    }

    @Test
    void reportsAnUnsupportedEffectWithItsExactJsonPath() {
        assertThatThrownBy(() -> parse("""
                {"effects":[{"type":"Damage","value":4},{"type":"Teleport","value":1}]}
                """))
                .isInstanceOf(CardCatalogValidationException.class)
                .hasMessageContaining("enemy 'foreuse'.movePattern[3].effects[1].type")
                .hasMessageContaining("Teleport");
    }

    @Test
    void resolvesAMoveThatReferencesACatalogCard() throws Exception {
        assertThat(parse("""
                {"cardId":"frappe"}
                """)).isEqualTo(catalog.require("frappe"));
    }

    @Test
    void returnsNothingForAMoveThatDeclaresNeitherACardNorEffects() throws Exception {
        assertThat(parse("""
                {"moveName":"Attente","intent":"Unknown"}
                """)).isNull();
    }

    private CardDefinition parse(String json) throws Exception {
        JsonNode move = OBJECT_MAPPER.readTree(json);
        return parser.parseMove(catalog, move, "foreuse", 3);
    }
}
