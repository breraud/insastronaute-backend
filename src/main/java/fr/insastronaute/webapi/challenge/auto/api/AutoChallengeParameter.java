package fr.insastronaute.webapi.challenge.auto.api;

import java.util.List;

/**
 * Décrit un champ configurable d'une phrase clé, pour que le panneau
 * d'administration dessine son formulaire sans rien connaître de l'implémentation
 * Java. Purement descriptif : le serveur valide de toute façon par le type.
 */
public record AutoChallengeParameter(
        String name,
        String label,
        AutoChallengeParameterType type,
        boolean required,
        // Un Object, pas un JsonNode : ce record est sérialisé par le Jackson 3
        // de la couche HTTP, qui ne connaît pas les nœuds de Jackson 2.
        Object defaultValue,
        Long minimum,
        Long maximum,
        List<String> options
) {
    public static AutoChallengeParameter integer(String name, String label, long minimum, long maximum) {
        return new AutoChallengeParameter(
                name, label, AutoChallengeParameterType.INTEGER, true, null, minimum, maximum, null);
    }

    /**
     * Le choix entre collection physique et virtuelle.
     *
     * <p>Une valeur par défaut, et non un champ vide : les phrases écrites avant
     * ce choix portent sur la collection physique, et doivent continuer de le
     * faire.</p>
     */
    public static AutoChallengeParameter collectionType(String name, String label) {
        return new AutoChallengeParameter(
                name, label, AutoChallengeParameterType.SELECT, true, "PHYSICAL", null, null,
                List.of("PHYSICAL", "VIRTUAL"));
    }

    public static AutoChallengeParameter cardCategory(String name, String label) {
        return new AutoChallengeParameter(
                name, label, AutoChallengeParameterType.CARD_CATEGORY, true, null, null, null, null);
    }

    /**
     * Un type de carte, designe par son identifiant.
     *
     * <p>Deux axes independants dans le catalogue : la categorie porte la rarete, le
     * type porte ce que la carte represente -- un bar, un lieu, une personne. Viser
     * « toutes les cartes bar » demande celui-ci, pas l'autre.</p>
     */
    public static AutoChallengeParameter cardType(String name, String label) {
        return new AutoChallengeParameter(
                name, label, AutoChallengeParameterType.CARD_TYPE, true, null, null, null, null);
    }

    /**
     * Une carte du catalogue, designee par son identifiant.
     *
     * <p>Comme une categorie : personne ne connait l'identifiant par coeur, le
     * panneau dessine un selecteur a partir du catalogue.</p>
     */
    public static AutoChallengeParameter card(String name, String label) {
        return new AutoChallengeParameter(
                name, label, AutoChallengeParameterType.CARD, true, null, null, null, null);
    }
}
