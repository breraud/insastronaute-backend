package fr.insastronaute.webapi.common.adminaudit.dto;

import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;

/**
 * De quelle portee sont les lignes qu'on veut lire.
 *
 * <p>Les lignes de portee globale n'ont pas de cible : un filtre sur
 * {@code targetUserId} ne les ramene donc jamais, et sans ce parametre elles
 * seraient consultables mais pas isolables. C'est le seul axe qui permet de
 * demander "montre-moi les gestes qui ont touche N joueurs".</p>
 *
 * <p>La regle n'est enoncee qu'une fois, par
 * {@link AdminActionType#isGlobalScope()} : reciter ici la liste des types
 * globaux ferait un troisieme endroit a tenir a jour, apres l'enumeration et
 * la contrainte {@code ck_admin_action_events_target_presence}.</p>
 */
public enum AdminActionScope {

    /** Les gestes qui nomment un joueur. */
    NOMINAL,

    /** Les gestes qui nomment une portee et un nombre de joueurs touches. */
    GLOBAL,

    /** Les deux : le registre tel qu'il s'ecrit. */
    ALL;

    public boolean accepts(AdminActionType action) {
        return switch (this) {
            case NOMINAL -> !action.isGlobalScope();
            case GLOBAL -> action.isGlobalScope();
            case ALL -> true;
        };
    }
}
