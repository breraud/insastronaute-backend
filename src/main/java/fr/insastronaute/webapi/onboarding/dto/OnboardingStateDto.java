package fr.insastronaute.webapi.onboarding.dto;

import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;

/**
 * L'etat d'accueil, et le recapitulatif que le joueur devra capturer.
 *
 * <p>Les quatre valeurs du recapitulatif sont composees ici et non cote front : elles
 * viennent toutes du serveur, et un front qui les assemblerait pourrait afficher au
 * joueur autre chose que ce qu'un respo verra dans sa file.</p>
 */
public record OnboardingStateDto(
        OnboardingStatus status,
        boolean genderChosen,
        Gender gender,
        String fullName,
        String email,
        String department,
        String rejectionReason
) {}
