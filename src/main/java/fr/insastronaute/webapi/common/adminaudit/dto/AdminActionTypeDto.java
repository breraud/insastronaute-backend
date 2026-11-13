package fr.insastronaute.webapi.common.adminaudit.dto;

import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;

/**
 * Un type d'action tel que la barre de filtres le propose.
 *
 * <p>Il existe pour que la page n'ait pas sa propre copie de la liste : une
 * valeur ajoutee a l'enumeration apparait dans le filtre sans toucher au
 * panel, et une valeur qui n'existe pas cote serveur ne peut pas y trainer.</p>
 *
 * @param globalScope vrai quand ce type n'a pas de cible - la page sait alors
 *                    qu'une ligne sans joueur est normale, sans reciter la
 *                    liste des types globaux.
 */
public record AdminActionTypeDto(AdminActionType action, boolean globalScope) {

    public static AdminActionTypeDto from(AdminActionType action) {
        return new AdminActionTypeDto(action, action.isGlobalScope());
    }
}
