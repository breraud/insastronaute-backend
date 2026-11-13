package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEngineEnemyFollowUpTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000f5");

    @Test
    void authoredEnemyMoveTaggedFollowUpStillAdvancesItsCardFollowUp() {
        CardDefinition move = new CardDefinition(
                "enemy-move", "Enemy move", "", 0, CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, Set.of(CardTag.FOLLOW_UP), List.of());
        CardDefinition followUp = new CardDefinition(
                "enemy-follow-up", "Enemy follow-up", "", 0, CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, Set.of(CardTag.FOLLOW_UP),
                List.of(new EffectDefinition(EffectType.DAMAGE, 3)));
        StatusInstance status = new StatusInstance(
                "CardFollowUp", 1, -1, "enemy-follow-up", 0);
        CombatantState hero = new CombatantState(
                "hero", "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CombatantState enemy = new CombatantState(
                "enemy", "enemies", ControllerType.AI, 30, 30, 0, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()), List.of(status));
        CombatState state = new CombatState(
                COMBAT_ID, 2, "v1", CombatStatus.ACTIVE, null, new RandomState(9),
                List.of(hero, enemy),
                List.of(new TimelineEntry("enemy", 0, 0), new TimelineEntry("hero", 1, 1)),
                "enemy");

        var result = new CombatEngine(
                new CardCatalog("v1", Map.of(move.id(), move, followUp.id(), followUp)),
                new RelicEngine(List.of())).handle(state,
                new AiPlayPatternCommand(COMBAT_ID, state.revision(), "enemy",
                        move.id(), List.of("hero"), 1));

        assertThat(result.accepted()).isTrue();
        assertThat(result.events()).filteredOn(CardPlayed.class::isInstance).hasSize(2);
        assertThat(result.state().combatants().stream()
                .filter(combatant -> "hero".equals(combatant.combatantId()))
                .findFirst().orElseThrow().hp()).isEqualTo(27);
    }

        @Test
        void authoredDisplayNameResolvesToTheStableFollowUpCardId() {
                CardDefinition followUp = new CardDefinition(
                                "frappe_de_debris", "Frappe de dÃ©bris", "", 0, CardType.ATTACK,
                                TargetingMode.SINGLE_ENEMY, Set.of(), List.of());
                CardCatalog catalog = new CardCatalog("v1", Map.of(followUp.id(), followUp));

                assertThat(catalog.find("Frappe de débris")).isSameAs(followUp);
        }
}
