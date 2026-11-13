package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class MECARelicHandler implements RelicHandler {

    private static final String CHARGE = "charge";

    /**
     * Ce que l'Autoréparation vaut avant d'avoir rien soigné (MECARelic.cs:6).
     *
     * <p>Sans elle, un combat où l'on ne se soigne pas ouvre la relique à zéro, la garde de
     * charge la renvoie aussitôt, et la relique ne fait jamais rien — ce qu'elle faisait.
     */
    private static final int INITIAL_CHARGE = 20;

    @Override
    public String id() {
        return "MECARelic";
    }

    /**
     * La charge suit la run, pas le combat : elle ne retombe qu'en étant dépensée.
     *
     * <p>C'est aussi la seule chose qui limite la relique. Elle ne compte plus les fois où elle a
     * servi : ce qu'elle a rendu, elle ne l'a plus, et il faut le regagner en se soignant avant
     * qu'elle puisse relever son porteur une seconde fois. Un combat long où l'on se soigne
     * beaucoup peut donc la voir servir plusieurs fois, ce qui était le but.
     */
    @Override
    public java.util.Map<String, Integer> runScopedMemory() {
        return java.util.Map.of(CHARGE, INITIAL_CHARGE);
    }

    /**
     * Compte le soin sans le retenir.
     *
     * <p>Elle l'avalait : le porteur ne regagnait pas un point de vie de tout le combat, et
     * n'avait en échange qu'une réserve qu'il ne touchait qu'en mourant. Le soin passe désormais
     * normalement et alimente la réserve en plus, ce qui fait de la relique un bonus plutôt qu'un
     * marché.
     *
     * <p>La quantité rendue est celle qu'on lui présente, avant que les points de vie manquants
     * ne la limitent : ce qui compte pour la réserve est ce que la carte offrait, pas ce que le
     * corps pouvait encore encaisser.
     */
    @Override
    public HealInterceptionResult onHeal(CombatState state, CombatantState owner, int amount, List<CombatEvent> events) {
        if (amount > 0) {
            return new HealInterceptionResult(amount,
                    owner.remembering(id(), CHARGE, Math.addExact(owner.recall(id(), CHARGE), amount)));
        }
        return new HealInterceptionResult(amount, owner);
    }

    @Override
    public ResolutionStep onDamageTaken(
            CombatantState owner,
            int amount,
            List<CombatantState> combatants,
            List<fr.insastronaute.webapi.sts.combat.model.TimelineEntry> timeline,
            fr.insastronaute.webapi.sts.combat.model.RandomState random,
            java.util.UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        int charge = owner.recall(id(), CHARGE);
        if (amount <= 0 || owner.hp() > 0 || charge <= 0) {
            return new ResolutionStep(combatants, timeline, events, random);
        }

        // La réserve est vidée en servant, et c'est elle seule qui empêche la relique de relever
        // son porteur indéfiniment : la fois suivante se paie en soins.
        CombatantState revived = rebuild(owner, Math.min(owner.maxHp(), charge))
                .remembering(id(), CHARGE, 0);
        List<CombatantState> updated = new java.util.ArrayList<>(combatants);
        replace(updated, revived);

        int retaliation = charge / 2;
        for (int index = 0; index < updated.size(); index++) {
            CombatantState target = updated.get(index);
            if (target.hp() <= 0 || target.teamId().equals(revived.teamId())) {
                continue;
            }
            int hpLost = Math.min(target.hp(), retaliation);
            CombatantState damaged = rebuild(target, target.hp() - hpLost);
            updated.set(index, damaged);
            events.add(new DamageApplied(combatId, revision, revived.combatantId(), target.combatantId(),
                    retaliation, 0, hpLost, target.armor(), damaged.hp()));
        }
        return new ResolutionStep(updated, timeline, events, random);
    }

    private static CombatantState rebuild(CombatantState combatant, int hp) {
        return new CombatantState(combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), hp, combatant.armor(), combatant.energy(), combatant.baseEnergy(),
                combatant.turnDelayTicks(), combatant.piles(), combatant.statuses(), combatant.relics(),
                combatant.intentCardId(), combatant.patternIndex(), combatant.relicMemory());
    }

    private static void replace(List<CombatantState> combatants, CombatantState replacement) {
        for (int index = 0; index < combatants.size(); index++) {
            if (combatants.get(index).combatantId().equals(replacement.combatantId())) {
                combatants.set(index, replacement);
                return;
            }
        }
    }
}
