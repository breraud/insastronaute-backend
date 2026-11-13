package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.DrawEventFactory;
import fr.insastronaute.webapi.sts.combat.engine.PileOperations;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.EnergyGained;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Panneau solaire : « Au début du combat, gagnez 2 énergie et piochez 2 cartes. »
 *
 * <p>Au début du <em>combat</em>, une seule fois. Le handler n'avait aucune mémoire : il rendait
 * ses 2 énergies à l'ouverture de chaque tour, indéfiniment. Il ne piochait rien non plus, si
 * bien que la moitié annoncée de la relique n'arrivait jamais et que l'autre moitié n'arrêtait
 * plus d'arriver.
 *
 * <p>Le don reste accroché au premier tour plutôt qu'à {@code onCombatStart}, comme dans Unity :
 * un tour s'ouvre sur sa dotation de base, et de l'énergie donnée avant serait balayée par cette
 * remise à zéro (SolarCoreRelic.cs:19).
 *
 * <p>C'est la forme à trois arguments du crochet qui est redéfinie : piocher peut vider la pioche
 * et forcer un mélange, et le hasard dépensé doit revenir au combat, faute de quoi le mélange
 * suivant repartirait du même point et redonnerait le même ordre.
 */
@Component
public class SolarCoreRelic implements RelicHandler {

    private static final String GRANTED = "granted";
    private static final int ENERGY = 2;
    private static final int CARDS = 2;

    @Override
    public String id() {
        return "SolarCoreRelic";
    }

    @Override
    public TurnStart onTurnStart(
            CombatState state,
            CombatantState owner,
            RandomState random,
            List<CombatEvent> events
    ) {
        if (owner.recall(id(), GRANTED) != 0) {
            return new TurnStart(owner, random);
        }

        PileOperations.DrawResult draw = PileOperations.draw(owner.piles(), CARDS, random);
        if (state != null && events != null) {
            events.addAll(DrawEventFactory.create(
                    state.combatId(), state.revision(), owner.combatantId(),
                    owner.piles().hand().size(), draw));
            events.add(new EnergyGained(
                    state.combatId(), state.revision(), owner.combatantId(),
                    ENERGY, owner.energy() + ENERGY));
        }

        CombatantState granted = new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), owner.energy() + ENERGY,
                owner.baseEnergy(), owner.turnDelayTicks(), draw.piles(), owner.statuses(),
                owner.relics(), owner.intentCardId(), owner.patternIndex(), owner.relicMemory());
        return new TurnStart(granted.remembering(id(), GRANTED, 1), draw.random());
    }
}
