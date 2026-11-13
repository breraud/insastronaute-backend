package fr.insastronaute.webapi.user.dto;

import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserDto(
        UUID id,
        String keycloakSub,
        String email,
        String displayName,
        Gender gender,
        UUID departmentId,
        String department,
        Long tokens,
        Long virtualCollectionPoints,
        Long physicalCollectionPoints,
        Long challengePoints,
        Long tournamentPoints,
        Boolean hiddenFromLeaderboards,
        /**
         * Ce compte porte-t-il un role de staff.
         *
         * <p>Vient de Keycloak, pas de la base : l'application ne connait pas les roles.
         * C'est une information d'affichage -- elle trie la liste, elle n'autorise rien.
         * L'autorisation reste le jeton, verifie a chaque requete.</p>
         */
        Boolean staff,
        /**
         * Ce compte porte-t-il le role etudiant limite (4A).
         *
         * <p>Vient de Keycloak : ce joueur a un acces restreint, pas de defis ni de
         * collection physique, et n'apparait pas sur les classements.</p>
         */
        Boolean studentLimited,
        /**
         * Ce compte suit une filiere par apprentissage.
         *
         * <p>Vient de la base et de nulle part ailleurs : la coche est posee a la main
         * par un administrateur, l'annuaire ne distinguant pas les apprentis.</p>
         */
        Boolean perf,
        /** Ou en est ce compte dans son accueil : pas inscrit, en attente, accepte, refuse. */
        OnboardingStatus onboardingStatus,
        /**
         * Quand ce joueur a ete supprime, sa fiche etant retenue par le registre.
         *
         * <p>Null pour un compte vivant. C'est ce qui distingue « supprime » de « existe » :
         * la ligne reste en base parce qu'une trace la nomme, pas parce que le joueur joue
         * encore.</p>
         */
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public AdminUserDto(
            UUID id,
            String keycloakSub,
            String email,
            String displayName,
            Gender gender,
            UUID departmentId,
            String department,
            Long tokens,
            Long virtualCollectionPoints,
            Long physicalCollectionPoints,
            Long challengePoints,
            Long tournamentPoints,
            Boolean hiddenFromLeaderboards,
            Boolean staff,
            OnboardingStatus onboardingStatus,
            Instant deletedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, keycloakSub, email, displayName, gender, departmentId, department, tokens,
                virtualCollectionPoints, physicalCollectionPoints, challengePoints, tournamentPoints,
                hiddenFromLeaderboards, staff, null, false, onboardingStatus, deletedAt, createdAt, updatedAt);
    }

    public static AdminUserDto from(AppUser user, String department) {
        return from(user, department, null, null);
    }

    public static AdminUserDto from(AppUser user, String department, Boolean staff) {
        return from(user, department, staff, null);
    }

    public static AdminUserDto from(AppUser user, String department, Boolean staff, Boolean studentLimited) {
        return new AdminUserDto(
                user.getId(),
                user.getKeycloakSub(),
                user.getEmail(),
                user.getDisplayName(),
                user.getGender(),
                user.getDepartment(),
                department,
                user.getTokens(),
                user.getVirtualCollectionPoints(),
                user.getPhysicalCollectionPoints(),
                user.getChallengePoints(),
                user.getTournamentPoints(),
                user.getHiddenFromLeaderboards(),
                staff,
                studentLimited,
                user.isPerf(),
                user.getOnboardingStatus(),
                user.getDeletedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
