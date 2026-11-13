package fr.insastronaute.webapi.challenge.auto.types;

import fr.insastronaute.webapi.card.domain.CardCollectionType;

/**
 * Sur quelle collection porte un objectif de cartes.
 *
 * <p>Absent, cela veut dire la collection physique : c'est ce que les phrases
 * écrites avant ce choix mesuraient, et une phrase déjà publiée ne doit pas
 * changer de sens parce qu'un champ est apparu.</p>
 *
 * <p>La distinction n'est pas cosmétique : seules les cartes physiques sont
 * mutualisées dans une équipe. Un objectif sur la collection virtuelle se mesure
 * donc joueur par joueur, et sa récompense ne se partage pas.</p>
 */
public final class CollectionScope {

    private CollectionScope() {
    }

    public static CardCollectionType parse(String value) {
        if (value == null || value.isBlank()) {
            return CardCollectionType.PHYSICAL;
        }
        return switch (value.trim().toUpperCase()) {
            case "VIRTUAL" -> CardCollectionType.VIRTUAL;
            case "PHYSICAL" -> CardCollectionType.PHYSICAL;
            default -> throw new IllegalArgumentException(
                    "La collection doit être PHYSICAL ou VIRTUAL.");
        };
    }

    /**
     * Ce qu'on ajoute à une phrase pour lever le doute.
     *
     * <p>Vide pour le physique : c'est ce que ces phrases ont toujours voulu dire,
     * et le préciser partout alourdirait des libellés déjà publiés. Le virtuel, lui,
     * se dit — c'est lui qui surprendrait.</p>
     */
    public static String suffix(CardCollectionType collectionType) {
        return collectionType == CardCollectionType.VIRTUAL ? " (collection virtuelle)" : "";
    }

    /** Seule la collection physique est mise en commun dans une équipe. */
    public static boolean isTeamScoped(CardCollectionType collectionType) {
        return collectionType == CardCollectionType.PHYSICAL;
    }
}
