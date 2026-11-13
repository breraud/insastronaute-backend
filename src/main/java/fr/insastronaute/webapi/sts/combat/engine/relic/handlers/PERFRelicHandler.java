package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.PileOperations;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Alternateur de performance : « Gagne 1 énergie en moins par tour. Quand vous subissez des
 * dégâts, jouez la carte du dessus de votre pioche. »
 *
 * <p>La carte était prise au mauvais bout de la pioche — {@code draw.size() - 1}, c'est-à-dire le
 * dessous, alors que {@link PileOperations#TOP_INDEX} est le dessus — puis simplement posée en
 * main au lieu d'être jouée. Rien ne se voyait donc à l'écran : aucune carte ne partait, et celle
 * qui arrivait n'était pas celle qu'on croyait.
 */
@Component
public class PERFRelicHandler implements RelicHandler {

    @Override
    public String id() {
        return "PERFRelic";
    }

    @Override
    public CombatantState onTurnStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        if (owner.energy() > 0) {
            // "Gagne 1 energie en moins par tour" -- announced, or the client shows the energy the
            // turn opened with and the player wonders where the missing point went.
            if (events != null && state != null) {
                events.add(new fr.insastronaute.webapi.sts.combat.event.EnergySpent(
                        state.combatId(), state.revision(), owner.combatantId(),
                        1, owner.energy() - 1));
            }
            return rebuild(owner, owner.energy() - 1, owner.piles());
        }
        return owner;
    }

    @Override
    public ResolutionStep onDamageTaken(CombatantState owner, int amount, List<CombatantState> combatants, List<TimelineEntry> timeline, RandomState random, UUID combatId, long revision, List<CombatEvent> events) {
        if (amount <= 0) {
            return new ResolutionStep(combatants, timeline, events, random);
        }

        List<CardInstance> drawPile = new ArrayList<>(owner.piles().draw());
        List<CardInstance> discard = new ArrayList<>(owner.piles().discard());
        RandomState currentRandom = random;

        // Une pioche vide se remplit de la défausse, exactement comme n'importe quelle pioche :
        // c'est ce que fait {@code DeckManager.GetAndRemoveTopCard} avant de servir
        // (DeckManager.cs:186). Sans ce mélange, la relique se taisait dès que la pioche était
        // épuisée — et comme elle en retire une carte par coup reçu, cela arrivait vite : un
        // enchaînement de tours d'ennemi, ou une seule volée de Multifrappe, suffisait à
        // l'éteindre pour le reste du combat.
        if (drawPile.isEmpty()) {
            if (discard.isEmpty()) {
                return new ResolutionStep(combatants, timeline, events, random);
            }
            PileOperations.ShuffleResult reshuffled = PileOperations.shuffle(discard, currentRandom);
            drawPile.addAll(reshuffled.cards());
            discard.clear();
            currentRandom = reshuffled.random();
            events.add(new fr.insastronaute.webapi.sts.combat.event.PileShuffled(
                    combatId, revision, owner.combatantId(), "DRAW",
                    reshuffled.cards().stream().map(CardInstance::instanceId).toList()));
        }

        CardInstance topCard = drawPile.remove(PileOperations.TOP_INDEX);

        // La carte quitte la pioche pour la défausse : elle est jouée depuis le dessus, jamais
        // détenue. La laisser en main en ferait une carte de plus à jouer, et le serveur
        // refuserait ensuite de la jouer une seconde fois.
        discard.add(topCard);

        CombatantState updatedOwner = rebuild(owner, owner.energy(),
                new CardPiles(drawPile, owner.piles().hand(), discard, owner.piles().exhaust()));

        List<CombatantState> newCombatants = new ArrayList<>(combatants);
        for (int i = 0; i < newCombatants.size(); i++) {
            if (newCombatants.get(i).combatantId().equals(updatedOwner.combatantId())) {
                newCombatants.set(i, updatedOwner);
            }
        }
        return new ResolutionStep(newCombatants, timeline, events, currentRandom)
                .asking(new ResolutionStep.PendingCardPlay(
                        owner.combatantId(), topCard.definitionId(), null, topCard.instanceId()));
    }

    private static CombatantState rebuild(CombatantState owner, int energy, CardPiles piles) {
        return new CombatantState(
                owner.combatantId(),
                owner.teamId(),
                owner.controllerType(),
                owner.maxHp(),
                owner.hp(),
                owner.armor(),
                energy,
                owner.baseEnergy(),
                owner.turnDelayTicks(),
                piles,
                owner.statuses(),
                owner.relics(),
                owner.intentCardId(),
                owner.patternIndex(), owner.relicMemory());
    }
}
