package fr.insastronaute.webapi.onboarding.service;

import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Un moderateur a-t-il declare son genre ?
 *
 * <p>La question n'est pas administrative : pour un defi marque sensible, un moderateur qui
 * n'est pas administrateur ne traite que les soumissions de son propre genre (voir
 * {@code ChallengeService#canModerateSubmission}). Tant que personne n'a repondu, la colonne
 * vaut NEUTRAL par defaut -- et ce defaut, que personne n'a choisi, decide seul quelles
 * preuves sensibles s'ouvrent et lesquelles disparaissent de la file. C'est un tri silencieux
 * sur une valeur que son porteur ignore.</p>
 *
 * <p>On lit l'horodatage du choix, jamais la valeur : NEUTRAL est un choix legitime autant
 * que le defaut de la colonne, donc la valeur seule ne dit pas si quelqu'un a repondu.</p>
 */
@Service
public class ModeratorGenderGateService {

    private final UserRepository userRepository;

    public ModeratorGenderGateService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Un compte qui n'existe pas encore en base n'a rien choisi.
     *
     * <p>Il sera cree a sa premiere lecture de session ou de profil, ou la question lui sera
     * posee : refuser ici est ce qui l'y amene, et rien n'est perdu en chemin.</p>
     */
    @Transactional(readOnly = true)
    public boolean hasChosenGender(String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return false;
        }

        return userRepository.findByKeycloakSub(keycloakSub)
                .map(user -> user.getOnboardingGenderChosenAt() != null)
                .orElse(false);
    }
}
