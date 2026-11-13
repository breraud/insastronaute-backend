package fr.insastronaute.webapi.sts.combat.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.EffectCondition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A combat is saved as JSON and read back into these records, so anything the engine writes must
 * survive the trip.
 *
 * <p>This is not a formality. A record method named the way a bean getter is named — {@code
 * isPlain()}, {@code isConditional()} — is written into the JSON as a property, and reading that
 * JSON back into a record that has no such component fails: a live combat becomes unloadable. It
 * happened in production, and the state grows new fields often enough that it will be tried again.
 */
class CombatStateRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void readsBackACombatItJustWrote() throws Exception {
        CombatState saved = fullyLoaded();

        JsonNode written = MAPPER.valueToTree(saved);
        CombatState read = MAPPER.treeToValue(written, CombatState.class);

        assertThat(read).isEqualTo(saved);
    }

    private static CombatState fullyLoaded() {
        CardInstance grafted = new CardInstance("card-1", "frappe")
                .withTag(CardTag.EXHAUST)
                .withAddedEffect(new EffectDefinition(
                        EffectType.DAMAGE, 3, false, null, 0, "", null,
                        null, null, List.of(), false, false, new EffectCondition(
                                fr.insastronaute.webapi.sts.combat.catalog.ConditionType.KILLING_BLOW,
                                "")));
        CombatantState hero = new CombatantState(
                "hero",
                "players",
                ControllerType.HUMAN,
                30,
                24,
                5,
                2,
                3,
                100,
                new CardPiles(
                        List.of(new CardInstance("card-2", "garde")),
                        List.of(grafted),
                        List.of(),
                        List.of()),
                List.of(
                        new StatusInstance("Vuln", 0, 2, "", 0),
                        StatusInstance.unremovable("Fragile", 0, 99)),
                List.of("SafetyNetRelic"),
                "intent",
                2,
                List.of(new RelicMemory("SafetyNetRelic", "used", 1)));
        CombatantState enemy = new CombatantState(
                "enemy",
                "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                2,
                2,
                200,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                UUID.fromString("00000000-0000-0000-0000-000000000161"),
                12,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(99),
                List.of(hero, enemy),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 100, 1)),
                "hero",
                CombatCounters.initial()
                        .afterPlaying("card-9", "frappe",
                                fr.insastronaute.webapi.sts.combat.catalog.CardType.ATTACK));
    }

    @Test
    void keepsEveryTagAndModifierACardPickedUp() throws Exception {
        CardInstance carried = new CardInstance("card-1", "frappe").withTag(CardTag.RETAIN);

        CardInstance read = MAPPER.treeToValue(MAPPER.valueToTree(carried), CardInstance.class);

        assertThat(read.tags()).contains(CardTag.RETAIN);
        assertThat(read).isEqualTo(carried);
    }
}
