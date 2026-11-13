package fr.insastronaute.webapi.user.dto;

import fr.insastronaute.webapi.user.domain.Gender;
import jakarta.validation.constraints.NotNull;

/**
 * La correction administrative du genre d'un joueur.
 *
 * <p>Elle existe parce que le champ est devenu immuable cote joueur : sans elle, une
 * erreur de saisie serait definitive sur un champ qui decide quel moderateur peut
 * consulter les preuves sensibles de ce compte.</p>
 */
public record AdminGenderUpdateDto(
        @NotNull Gender gender
) {}
