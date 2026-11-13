package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seau d'eau : « Toutes les 9 Compétences jouées, gagnez 1 énergie. »
 *
 * <p>Le handler donnait 9 d'énergie à l'ouverture de chaque combat, sans rien compter. Unity
 * compte les Compétences sur l'instance de la relique, qui vit toute la run : le compte est donc
 * gardé d'un combat à l'autre.
 */
@Component
public class NineSkillsBatteryRelic implements RelicHandler {

    static final String SKILLS_PLAYED = "skillsPlayed";
    static final int SKILLS_PER_ENERGY = 9;

    @Override
    public String id() {
        return "NineSkillsBatteryRelic";
    }

    @Override
    public Map<String, Integer> runScopedMemory() {
        return Map.of(SKILLS_PLAYED, 0);
    }

    @Override
    public ResolutionStep onCardPlayed(
            CombatantState owner,
            CardDefinition card,
            CombatCounters counters,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        if (card == null || card.type() != CardType.SKILL) {
            return new ResolutionStep(combatants, timeline, events, random);
        }
        int played = (owner.recall(id(), SKILLS_PLAYED) + 1) % SKILLS_PER_ENERGY;
        CombatantState counted = owner.remembering(id(), SKILLS_PLAYED, played);
        List<CombatantState> roster = replace(combatants, counted);
        if (played != 0) {
            return new ResolutionStep(roster, timeline, events, random);
        }
        return RelicActions.gainEnergy(counted, 1, roster, timeline, random, combatId, revision,
                events);
    }

    private static List<CombatantState> replace(List<CombatantState> combatants, CombatantState updated) {
        List<CombatantState> result = new ArrayList<>(combatants);
        for (int index = 0; index < result.size(); index++) {
            if (result.get(index).combatantId().equals(updated.combatantId())) {
                result.set(index, updated);
                break;
            }
        }
        return result;
    }
}
