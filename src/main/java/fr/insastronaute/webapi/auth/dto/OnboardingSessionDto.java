package fr.insastronaute.webapi.auth.dto;

import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;

/**
 * L'etat d'accueil tel que la session le voit.
 *
 * <p>{@code genderChosen} ne se deduit pas de {@code gender} : NEUTRAL est a la fois un choix
 * legitime et la valeur par defaut de la colonne. Seul l'horodatage du choix distingue « a
 * repondu neutre » de « n'a jamais repondu », et c'est cette distinction que le PanelAdmin lit
 * pour savoir s'il doit poser la question a un membre du staff.</p>
 */
public record OnboardingSessionDto(
        OnboardingStatus status,
        String rejectionReason,
        boolean genderChosen,
        Gender gender
) {
    public static OnboardingSessionDto from(AppUser user) {
        return new OnboardingSessionDto(
                user.getOnboardingStatus(),
                user.getOnboardingRejectionReason(),
                user.getOnboardingGenderChosenAt() != null,
                user.getGender()
        );
    }
}
