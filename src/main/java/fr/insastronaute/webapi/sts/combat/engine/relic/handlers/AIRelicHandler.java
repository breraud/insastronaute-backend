package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import org.springframework.stereotype.Component;

/**
 * Intemporalité : « Les effets appliqués durent 2 tours de plus. »
 *
 * <p>Deux choses manquaient à cette relique pour faire ce que sa description promet.
 *
 * <p>Elle ajoutait 2 à la <em>valeur</em> du statut là où Unity en allonge la <em>durée</em>
 * ({@code StatusEffect.Extend}). Les deux ne se ressemblent nulle part : un statut de forme
 * {@code DURATION} — Vulnérable, Affaibli, Brûlure, Poison, Étourdissement, Régénération — ne lit
 * jamais sa valeur, si bien que la relique ne faisait rien du tout sur la moitié du jeu ; sur un
 * statut de forme {@code VALUE} elle donnait 2 points de Force ou de Dextérité, ce que personne ne
 * lui avait demandé.
 *
 * <p>Et elle décidait du bord au {@code ControllerType} : un buff comptait s'il tombait sur un
 * HUMAIN, un debuff s'il tombait sur autre chose. En duel les deux joueurs sont humains, donc
 * aucun debuff n'était jamais allongé — la moitié de la relique du personnage des statuts ne
 * servait à rien précisément là où il joue contre quelqu'un. Le bord se lit maintenant sur
 * l'équipe, la seule chose qui distingue les deux camps dans les deux modes. C'est la même faute
 * que {@link CFIRelicHandler} portait, et elle se répare de la même façon.
 *
 * <p>Les règles de l'allongement sont celles de {@code ExtendStatusesHandler}, qui les tient
 * d'Unity : un statut marqué inextensible ne bouge pas, et un statut permanent non plus — il n'a
 * pas d'horloge à faire grandir.
 */
@Component
public class AIRelicHandler implements RelicHandler {

    /** Ce que la relique ajoute, en tours. */
    private static final int EXTRA_TURNS = 2;

    @Override
    public String id() {
        return "AIRelic";
    }

    @Override
    public StatusInstance onStatusApply(CombatantState source, CombatantState target, StatusInstance status) {
        if (source == null || target == null || status == null) {
            return status;
        }
        // Inextensible, ou permanent : rien à allonger.
        if (StatusCatalog.of(status.statusType()).inextendable() || status.duration() <= 0) {
            return status;
        }

        boolean ownSide = source.teamId() != null && source.teamId().equals(target.teamId());
        boolean extends2 = ownSide
                ? StatusCatalog.isBuff(status)
                : StatusCatalog.isDebuff(status);
        if (!extends2) {
            return status;
        }

        return new StatusInstance(
                status.statusType(),
                status.value(),
                status.duration() + EXTRA_TURNS,
                status.cardId(),
                status.index()
        );
    }
}
