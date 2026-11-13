package fr.insastronaute.webapi.friend.dto;

public record FriendProfileDto(
        String fullName,
        String department,
        long commonFriendsCount,
        double virtualCardsCompletionPercent,
        double physicalCardsCompletionPercent,
        double challengesCompletionPercent,
        Integer challengePointsRank,
        /**
         * Cet ami suit une filiere par apprentissage.
         *
         * <p>Son departement reste celui de sa filiere : c'est lui qui colore la fiche et
         * choisit sa planete. La marque s'ajoute a cote, elle ne le remplace pas.</p>
         */
        boolean perf
) {
}
