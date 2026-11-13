package fr.insastronaute.webapi.user.dto;

import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;

public record UserProfileDto(
        String fullName,
        Gender gender,
        String department,
        Long tokens,
        Long virtualCollectionPoints,
        Long physicalCollectionPoints,
        boolean passkeySuggestionDue,
        Long challengePoints,
        Long tournamentPoints,
        /** Points Events : la monnaie des événements, gagnée avec les tokens. */
        long eventPoints,
        /**
         * Ce joueur suit une filière par apprentissage.
         *
         * <p>Son département reste celui de sa filière : la marque s'ajoute à côté de
         * la puce, elle ne la remplace pas.</p>
         */
        boolean perf
) {
    /**
     * Collection points are passed in rather than read off the user: they are
     * computed from the collection on read, and the columns on AppUser are a
     * leftover that never gets written.
     */
    public static UserProfileDto from(
            AppUser user, String department, long virtualCollectionPoints, long physicalCollectionPoints
    ) {
        return new UserProfileDto(
                user.getDisplayName(),
                user.getGender(),
                department,
                user.getTokens(),
                virtualCollectionPoints,
                physicalCollectionPoints,
                user.isPasskeySuggestionDue(java.time.Instant.now()),
                user.getChallengePoints(),
                user.getTournamentPoints(),
                user.getEventPoints(),
                user.isPerf()
        );
    }
}
