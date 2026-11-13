package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.EnergySpent;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Termine le tour de celui qui joue la carte.
 *
 * <p>Le handler ne faisait que mettre l'énergie du lanceur à zéro, ce qui n'a jamais terminé un
 * tour : le tour se ferme sur une commande, pas sur une bourse vide. Le joueur restait donc à
 * jouer, sans énergie, devant une carte qui promettait le contraire ; et une carte jouée avec
 * l'énergie déjà à zéro ne faisait strictement rien, puisque le handler se retirait avant même
 * d'écrire son événement. Unity, lui, appelle {@code turnSystem.PlayerEndTurn()}
 * (EffectResolver.cs:446).
 *
 * <p>Le tour est demandé plutôt que fermé sur place : la cérémonie de fin de tour appartient à
 * {@code CombatEngine}, et elle attend de toute façon que la carte ait fini de se résoudre.
 */
public final class EndTurnHandler implements EffectHandler {

    @Override
    public ResolutionStep handle(
            UUID combatId,
            long revision,
            String sourceId,
            CombatantState target,
            EffectDefinition effect,
            RandomState random,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            CardDefinition card, java.util.List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> cardModifiers
    ) {
        // Le tour qui se termine est celui du lanceur, jamais celui de la cible : une carte
        // pointée sur un adversaire ne lui prend pas son tour, elle finit celui de qui la joue.
        CombatantState caster = combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(sourceId))
                .findFirst()
                .orElse(target);

        List<CombatEvent> events = new ArrayList<>();
        List<CombatantState> updated = new ArrayList<>(combatants);
        if (caster.energy() > 0) {
            // L'énergie part avec le tour : la garder d'un tour sur l'autre en ferait une
            // réserve, alors qu'un tour s'ouvre toujours sur sa dotation de base.
            events.add(new EnergySpent(
                    combatId, revision, caster.combatantId(), caster.energy(), 0));
            CombatantState spent = withEnergy(caster, 0);
            for (int index = 0; index < updated.size(); index++) {
                if (updated.get(index).combatantId().equals(spent.combatantId())) {
                    updated.set(index, spent);
                }
            }
        }

        return new ResolutionStep(updated, timeline, events, random).endingTheTurn();
    }

    private static CombatantState withEnergy(CombatantState combatant, int energy) {
        return new CombatantState(
                combatant.combatantId(),
                combatant.teamId(),
                combatant.controllerType(),
                combatant.maxHp(),
                combatant.hp(),
                combatant.armor(),
                energy,
                combatant.baseEnergy(),
                combatant.turnDelayTicks(),
                combatant.piles(),
                combatant.statuses(),
                combatant.relics(),
                combatant.intentCardId(),
                combatant.patternIndex(), combatant.relicMemory());
    }
}
