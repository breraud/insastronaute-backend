package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.card.domain.CollectionTestSettings;
import fr.insastronaute.webapi.card.repository.CollectionTestSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * L'interrupteur qui fait voir à tout le monde une collection complète.
 *
 * Il ne donne rien : aucune ligne de collection n'est écrite, et le refermer
 * suffit à revenir à l'état réel. Ce que chacun possède vraiment continue de
 * décider du classement, des succès et des points de collection — seul l'usage
 * est ouvert.
 */
@Service
public class CollectionTestModeService {

    private final CollectionTestSettingsRepository settingsRepository;

    public CollectionTestModeService(CollectionTestSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @Transactional(readOnly = true)
    public boolean allCardsOwned() {
        return settingsRepository.findById(true)
                .map(CollectionTestSettings::isAllCardsOwned)
                .orElse(false);
    }

    @Transactional
    public boolean setAllCardsOwned(boolean allCardsOwned) {
        CollectionTestSettings settings = settingsRepository.findById(true).orElseGet(CollectionTestSettings::new);
        settings.setAllCardsOwned(allCardsOwned);
        return settingsRepository.save(settings).isAllCardsOwned();
    }
}
