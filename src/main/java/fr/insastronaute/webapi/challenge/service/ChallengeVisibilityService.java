package fr.insastronaute.webapi.challenge.service;

import fr.insastronaute.webapi.challenge.domain.ChallengeVisibilitySettings;
import fr.insastronaute.webapi.challenge.repository.ChallengeVisibilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Masquer ou rendre les défis, tous à la fois.
 *
 * Aucun défi n'est modifié : c'est un rideau, pas une édition. Le rouvrir rend
 * exactement ce qui était là, sans avoir à se rappeler ce qu'on avait éteint.
 */
@Service
public class ChallengeVisibilityService {

    private final ChallengeVisibilityRepository repository;

    public ChallengeVisibilityService(ChallengeVisibilityRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean hidden() {
        return repository.findById(true).map(ChallengeVisibilitySettings::isHidden).orElse(false);
    }

    @Transactional
    public boolean setHidden(boolean hidden) {
        ChallengeVisibilitySettings settings = repository.findById(true)
                .orElseGet(ChallengeVisibilitySettings::new);
        settings.setHidden(hidden);
        return repository.save(settings).isHidden();
    }
}
