package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MECARelicHandlerTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000081");

    /**
     * Le soin passe et compte : la relique ne l'echange plus contre sa reserve.
     *
     * <p>Elle le retenait, si bien que son porteur ne regagnait pas un point de vie de tout le
     * combat et n'avait en echange qu'une reserve qu'il ne touchait qu'en mourant.
     */
    @Test
    void onHealLetsTheHealThroughAndBanksItAsWell() {
        MECARelicHandler handler = new MECARelicHandler();

        CombatantState owner = combatant(30);
        CombatState state = state(owner);
        List<CombatEvent> events = new ArrayList<>();

        RelicHandler.HealInterceptionResult result = handler.onHeal(state, owner, 10, events);

        assertThat(result.remainingHeal())
                .describedAs("le soin est rendu tel quel")
                .isEqualTo(10);
        assertThat(result.owner().recall("MECARelic", "charge")).isEqualTo(10);
    }

    @Test
    void revivesAndDamagesEveryOpponentWhenLethalDamageLands() {
        MECARelicHandler handler = new MECARelicHandler();
        CombatantState owner = combatant(0).remembering("MECARelic", "charge", 20);
        CombatantState enemy = new CombatantState(
            "enemy", "enemy-team", ControllerType.AI,
            30, 30, 7, 3, 3, 100,
            new CardPiles(List.of(), List.of(), List.of(), List.of()),
            List.of(), List.of(), null, 0);
        List<CombatEvent> events = new ArrayList<>();

        ResolutionStep result = handler.onDamageTaken(
            owner, 8, List.of(owner, enemy), List.of(), new RandomState(4), COMBAT_ID, 13, events);

        assertThat(combatant(result.combatants(), "hero").hp()).isEqualTo(20);
        assertThat(combatant(result.combatants(), "enemy").hp()).isEqualTo(20);
        assertThat(result.events()).filteredOn(DamageApplied.class::isInstance).singleElement()
            .isInstanceOfSatisfying(DamageApplied.class, event -> {
                assertThat(event.armorAbsorbed()).isZero();
                assertThat(event.hpLost()).isEqualTo(10);
            });
    }

    /**
     * Vider la reserve est tout ce qui la retient.
     *
     * <p>Un drapeau « deja servi ce combat » l'arretait ; il a disparu, et c'est desormais la
     * reserve seule qui limite — elle se regagne en se soignant, et la relique peut alors
     * relever son porteur une seconde fois dans le meme combat.
     */
    @Test
    void spendingTheReserveIsWhatKeepsItFromSavingAgain() {
        MECARelicHandler handler = new MECARelicHandler();
        CombatantState owner = combatant(0).remembering("MECARelic", "charge", 20);

        ResolutionStep saved = handler.onDamageTaken(
            owner, 8, List.of(owner), List.of(), new RandomState(4), COMBAT_ID, 13,
            new ArrayList<>());
        CombatantState revived = combatant(saved.combatants(), "hero");

        assertThat(revived.recall("MECARelic", "charge"))
                .describedAs("la reserve est videe en servant")
                .isZero();

        // Retombe a zero sans s'etre soigne : il n'y a plus rien a rendre.
        ResolutionStep again = handler.onDamageTaken(
            combatant(0), 8, List.of(combatant(0)), List.of(), new RandomState(4), COMBAT_ID, 14,
            new ArrayList<>());

        assertThat(combatant(again.combatants(), "hero").hp()).isZero();
    }

    private static CombatantState combatant(int hp) {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, hp, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(), List.of("MECARelic"), null, 0);
    }

    private static CombatState state(CombatantState combatant) {
        return new CombatState(
                COMBAT_ID,
                12,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(123L),
                List.of(combatant, new CombatantState("enemy", "enemy-team", ControllerType.AI,
                        10, 10, 0, 3, 3, 100,
                        new CardPiles(List.of(), List.of(), List.of(), List.of()),
                        List.of(), List.of(), null, 0)),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 1, 1)),
                "hero");
    }

    private static CombatantState combatant(List<CombatantState> combatants, String combatantId) {
        return combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }
}
