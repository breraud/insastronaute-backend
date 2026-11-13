package fr.insastronaute.webapi.sts.combat.ai;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import java.util.List;
import java.util.Objects;

public final class SingleCardEnemyPolicy implements EnemyPolicy {

    private final CardCatalog catalog;

    public SingleCardEnemyPolicy(CardCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    }

    @Override
    public CombatCommand nextCommand(CombatState state) {
        Objects.requireNonNull(state, "state must not be null");
        if (state.status() != CombatStatus.ACTIVE) {
            throw new IllegalArgumentException("combat must be active");
        }
        CombatantState actor = state.combatants().stream()
                .filter(combatant -> combatant.combatantId()
                        .equals(state.activeCombatantId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "active combatant is missing"));
        if (actor.controllerType() != ControllerType.AI || actor.hp() <= 0) {
            throw new IllegalArgumentException("active combatant must be a living AI");
        }

        return predictCommand(state, actor);
    }

    @Override
    public CombatCommand predictCommand(CombatState state, CombatantState actor) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(actor, "actor must not be null");

        for (CardInstance card : actor.piles().hand()) {
            CardDefinition definition;
            try {
                definition = catalog.require(card.definitionId());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "AI hand references an unknown card definition", exception);
            }
            if (definition.cost() > actor.energy()) {
                continue;
            }
            List<String> targets = targets(state, actor, definition.targetingMode());
            if (!targets.isEmpty()) {
                return new PlayCardCommand(
                        state.combatId(),
                        state.revision(),
                        actor.combatantId(),
                        card.instanceId(),
                        targets);
            }
        }
        return new EndTurnCommand(
                state.combatId(), state.revision(), actor.combatantId());
    }

    private static List<String> targets(
            CombatState state,
            CombatantState actor,
            TargetingMode targetingMode
    ) {
        if (targetingMode == TargetingMode.SELF) {
            return List.of(actor.combatantId());
        }
        if (targetingMode == TargetingMode.ANY_PLAYER) {
            return state.combatants().stream()
                    .filter(combatant -> combatant.hp() > 0)
                    .filter(combatant -> combatant.teamId().equals(actor.teamId()))
                    .map(CombatantState::combatantId)
                    .findFirst()
                    .map(List::of)
                    .orElseGet(List::of);
        }
        if (targetingMode == TargetingMode.SINGLE_ENEMY) {
            return state.combatants().stream()
                    .filter(combatant -> combatant.hp() > 0)
                    .filter(combatant -> !combatant.teamId().equals(actor.teamId()))
                    .map(CombatantState::combatantId)
                    .findFirst()
                    .map(List::of)
                    .orElseGet(List::of);
        }
        return List.of();
    }
}
