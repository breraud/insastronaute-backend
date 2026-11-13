package fr.insastronaute.webapi.common.adminaudit.dto;

import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Une ligne du registre telle que la page la lit.
 *
 * @param actor l'administrateur auteur, ou {@code null} sur une ligne reprise
 *              dont le moderateur d'origine n'a pas pu etre retrouve. Meme
 *              traitement que la cible : l'absence est rendue par l'absence,
 *              et la page l'affiche comme un auteur inconnu.
 * @param target le joueur touche, ou {@code null} sur un geste de portee
 *               globale. Rendre l'absence par un objet vide dirait "un joueur
 *               dont on a perdu le nom" ; l'absence dit "ce geste n'en nommait
 *               aucun", et c'est {@code globalScope} qui explique pourquoi.
 * @param details les details relus, pas la chaine JSON brute : la page ne doit
 *                pas avoir a reparser ce que le serveur a deja lu.
 */
public record AdminActionEventDto(
        UUID id,
        Instant occurredAt,
        AdminActionUser actor,
        AdminActionUser target,
        AdminActionType action,
        boolean globalScope,
        Map<String, Object> details,
        boolean backfilled
) {

    /** Un participant du registre, resolu en nom une fois pour toute la page. */
    public record AdminActionUser(UUID id, String displayName) {}
}
