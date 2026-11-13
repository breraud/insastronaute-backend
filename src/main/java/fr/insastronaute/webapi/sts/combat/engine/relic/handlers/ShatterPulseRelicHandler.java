package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShatterPulseRelicHandler implements RelicHandler {

    @Override
    public String id() {
        return "ShatterPulseRelic";
    }

    // Reactions en chaine -- « Lorsque l'Armure d'un ennemi se brise, infligez 3 degats aux autres
    // ennemis » (ShatterPulseRelic.cs) -- ne fait rien aujourd'hui, et c'est le seul TODO du lot
    // qui disait vrai : ses quatre voisines attendaient un compteur de tours et une memoire qui
    // existaient deja, celle-ci attend une capacite qui manque pour de bon.
    //
    // Les deux hooks disponibles ne peuvent pas l'ecrire. onTargetArmorBroken(owner, target) rend
    // le porteur, et RelicEngine.processTargetArmorBroken ne recoit rien d'autre ; la variante a
    // timeline rend la timeline. Aucune des deux n'a la liste des combattants, ni le combatId et
    // la revision qu'un DamageApplied exige.
    //
    // Le modele existe : MECARelicHandler.onDamageTaken rend un ResolutionStep et frappe ainsi
    // tous les adversaires. Il faudrait le meme ici -- un hook rendant un ResolutionStep, et les
    // appelants de processTargetArmorBroken dans CombatEngine qui lui passent les combattants,
    // le combatId et la revision. C'est une modification du moteur, pas d'un handler.
}
