package fr.insastronaute.webapi.sts.combat.engine.effect;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Follow-up statuses inherit their category from their referenced card, exactly as Unity's
 * FollowUpStatus constructor does. A normal-card follow-up such as Fioriture is a framed buff,
 * not an unframed neutral/debuff just because the status type itself carries no fixed category.
 */
class FollowUpStatusCategoryTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000f3");
    private static final String FIORITURE = "Fioriture";
    private static final StatusInstance FIORITURE_FOLLOW_UP =
            new StatusInstance("CardFollowUp", 1, -1, FIORITURE, 0);

    @Test
    void architectureDoesNotTransferFiorituresFramedBuff() {
        CombatantState owner = combatant("owner", "players", List.of(FIORITURE_FOLLOW_UP));
        CombatantState enemy = combatant("enemy", "enemies", List.of());

        ResolutionStep step = new TransferDebuffHandler(catalog()).handle(
                COMBAT_ID, 1, "owner", enemy,
                new EffectDefinition(EffectType.TRANSFER_DEBUFF, -1, false, null, 0, "", null),
                new RandomState(1), List.of(owner, enemy), List.of(), null, List.of());

        assertThat(statusesOf(step, "owner")).contains(FIORITURE_FOLLOW_UP);
        assertThat(statusesOf(step, "enemy")).doesNotContain(FIORITURE_FOLLOW_UP);
    }

    @Test
    void debuffDispelDoesNotRemoveFiorituresFramedBuff() {
        CombatantState owner = combatant("owner", "players", List.of(FIORITURE_FOLLOW_UP));

        ResolutionStep step = new DispelDebuffHandler(catalog()).handle(
                COMBAT_ID, 1, "enemy", owner,
                new EffectDefinition(EffectType.DISPEL_DEBUFF, -1, false, null, 0, "", null),
                new RandomState(1), List.of(owner), List.of(), null, List.of());

        assertThat(statusesOf(step, "owner")).contains(FIORITURE_FOLLOW_UP);
    }

    @Test
    void aTrueBuffDispelCanStillRemoveFiorituresFramedBuff() {
        CombatantState owner = combatant("owner", "players", List.of(FIORITURE_FOLLOW_UP));

        ResolutionStep step = new DispelBuffHandler(catalog()).handle(
                COMBAT_ID, 1, "enemy", owner,
                new EffectDefinition(EffectType.DISPEL_BUFF, -1, false, null, 0, "", null,
                        null, null, List.of(), false, true, null, 0),
                new RandomState(1), List.of(owner), List.of(), null, List.of());

        assertThat(statusesOf(step, "owner")).doesNotContain(FIORITURE_FOLLOW_UP);
    }

    private static CardCatalog catalog() {
        CardDefinition fioriture = new CardDefinition(
                FIORITURE, FIORITURE, "", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                java.util.Set.of(), List.of());
        return new CardCatalog("v1", Map.of(FIORITURE, fioriture));
    }

    private static CombatantState combatant(
            String id, String team, List<StatusInstance> statuses) {
        return new CombatantState(
                id, team, ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()), statuses);
    }

    private static List<StatusInstance> statusesOf(ResolutionStep step, String combatantId) {
        return step.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow()
                .statuses();
    }
}
