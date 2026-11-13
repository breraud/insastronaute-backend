package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Met un cadre autour de statuts déjà posés, pour les mettre hors de portée des dissipations.
 *
 * <p>Le cadre est déjà ce que lisent {@code StatusCatalog.isDispellable} et
 * {@code resistsEverything} pour décider si un statut se laisse emporter ; jusqu'ici, seul le
 * catalogue en décidait, et une carte ne pouvait donc pas protéger ce qu'elle venait de poser.
 * L'instance porte les deux exceptions — {@code removable} et {@code resistsEverything} — et
 * c'est celles-là qu'on écrit.
 *
 * <p>{@code trueEffect} choisit le cadre d'or : un statut que même un effet ignorant les
 * résistances ne peut plus retirer.
 *
 * <p>Les statuts sont pris dans l'ordre où le porteur les tient, et ceux qui sont déjà protégés
 * au niveau demandé sont passés : encadrer deux fois n'apporte rien, et compter un statut déjà
 * encadré ferait perdre une charge pour rien.
 */
public final class FrameStatusesHandler implements EffectHandler {

    /** Whether this handler frames the buffs on the target rather than the debuffs. */
    private final boolean framesBuffs;

    public FrameStatusesHandler(boolean framesBuffs) {
        this.framesBuffs = framesBuffs;
    }

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
            CardDefinition card,
            List<StatModifier> cardModifiers
    ) {
        boolean gold = effect.trueEffect();
        int remaining = effect.value() < 0 ? Integer.MAX_VALUE : effect.value();
        if (remaining == 0) {
            return new ResolutionStep(target, timeline, List.of(), random);
        }

        List<StatusInstance> statuses = new ArrayList<>(target.statuses());
        List<CombatEvent> events = new ArrayList<>();
        for (int index = 0; index < statuses.size() && remaining > 0; index++) {
            StatusInstance status = statuses.get(index);
            if (!matches(status) || alreadyFramed(status, gold)) {
                continue;
            }
            statuses.set(index, framed(status, gold));
            remaining--;
            // Annoncé comme une pose de statut : rien n'a changé de la valeur ni de la durée,
            // mais le client redessine le cadre à partir de ce qu'on lui envoie.
            events.add(new StatusApplied(combatId, revision, sourceId, target.combatantId(),
                    status.statusType(), status.value(), status.duration(), false));
        }
        if (events.isEmpty()) {
            return new ResolutionStep(target, timeline, List.of(), random);
        }
        return new ResolutionStep(withStatuses(target, statuses), timeline, events, random);
    }

    private boolean matches(StatusInstance status) {
        return framesBuffs ? StatusCatalog.isBuff(status) : StatusCatalog.isDebuff(status);
    }

    /** @return true when this instance already resists at least as much as the effect would give */
    private static boolean alreadyFramed(StatusInstance status, boolean gold) {
        if (gold) {
            return StatusCatalog.resistsEverything(status);
        }
        return StatusCatalog.resistsEverything(status)
                || !StatusCatalog.isDispellable(status, false);
    }

    private static StatusInstance framed(StatusInstance status, boolean gold) {
        return new StatusInstance(
                status.statusType(),
                status.value(),
                status.duration(),
                status.cardId(),
                status.index(),
                // Un cadre ordinaire ferme la porte aux cartes ordinaires ; le cadre d'or la
                // ferme à tout le monde, et il faut alors que les deux le disent.
                Boolean.FALSE,
                gold ? Boolean.TRUE : status.resistsEverything(),
                status.progress());
    }

    private static CombatantState withStatuses(
            CombatantState combatant, List<StatusInstance> statuses) {
        return new CombatantState(
                combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), combatant.hp(), combatant.armor(), combatant.energy(),
                combatant.baseEnergy(), combatant.turnDelayTicks(), combatant.piles(), statuses,
                combatant.relics(), combatant.intentCardId(), combatant.patternIndex(),
                combatant.relicMemory());
    }
}
