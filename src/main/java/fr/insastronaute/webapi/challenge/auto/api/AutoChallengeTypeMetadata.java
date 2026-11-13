package fr.insastronaute.webapi.challenge.auto.api;

import java.util.List;

public record AutoChallengeTypeMetadata(
        String key,
        String publicName,
        String description,
        List<AutoChallengeParameter> parameters,
        /** L'objectif se mesure sur l'équipe, et sa récompense s'y partage. */
        boolean teamScoped
) {
}
