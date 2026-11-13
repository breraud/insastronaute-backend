package fr.insastronaute.webapi.card.goofy;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.repository.CardInstanceRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

/**
 * Le code goofy et la carte qu'il donne.
 *
 * <p>La comparaison ignore la casse et les séparateurs : le code se saisit dans
 * le même champ que celui d'une carte imprimée, qui le remet en forme au fur et
 * à mesure de la frappe. Comparer les chaînes telles quelles rejetterait un code
 * pourtant juste, pour un tiret posé par le champ lui-même.</p>
 */
@Service
public class GoofyCardService {

    private static final String PUBLIC_ID_ALPHABET = "ABCDEFGHIJKLMNPQRSTUVWXYZ123456789";
    private static final int PUBLIC_ID_RAW_LENGTH = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GoofyCardSettingsRepository settingsRepository;
    private final GoofyCardRedemptionRepository redemptionRepository;
    private final CardRepository cardRepository;
    private final CardInstanceRepository instanceRepository;

    public GoofyCardService(
            GoofyCardSettingsRepository settingsRepository,
            GoofyCardRedemptionRepository redemptionRepository,
            CardRepository cardRepository,
            CardInstanceRepository instanceRepository
    ) {
        this.settingsRepository = settingsRepository;
        this.redemptionRepository = redemptionRepository;
        this.cardRepository = cardRepository;
        this.instanceRepository = instanceRepository;
    }

    public String generateUniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder raw = new StringBuilder(PUBLIC_ID_RAW_LENGTH);
            for (int index = 0; index < PUBLIC_ID_RAW_LENGTH; index++) {
                raw.append(PUBLIC_ID_ALPHABET.charAt(RANDOM.nextInt(PUBLIC_ID_ALPHABET.length())));
            }
            String formatted = raw.substring(0, 5) + "-" + raw.substring(5, 10) + "-" + raw.substring(10, 15);
            if (!instanceRepository.existsByPublicId(formatted)) {
                return formatted;
            }
        }
        throw new ConflictException("Impossible de générer un identifiant public unique.");
    }

    @Transactional(readOnly = true)
    public boolean matchesCode(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return false;
        }
        String configuredCode = normalize(settings().getCode());
        if (configuredCode.isEmpty()) {
            return false;
        }
        return configuredCode.equals(normalize(publicId));
    }

    /**
     * Les réglages, carte comprise : ce qu'on en lit sort de la transaction, et
     * un proxy paresseux ne survivrait pas au voyage.
     */
    @Transactional(readOnly = true)
    public GoofyCardSettings settings() {
        return settingsRepository.findWithCardById(GoofyCardSettings.SINGLETON_ID)
                .orElseGet(GoofyCardSettings::new);
    }

    /**
     * La carte réservée au code goofy, même lorsque le code n'est pas encore
     * activé. Dès qu'elle est choisie dans les réglages, elle ne doit plus
     * dévoiler sa place dans la collection avant son obtention.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> configuredCardId() {
        return Optional.ofNullable(settings().getCard()).map(Card::getId);
    }

    @Transactional
    public GoofyCardSettings update(UUID cardId, String code, boolean enabled) {
        String normalized = normalize(code);
        if (enabled && (cardId == null || normalized.isEmpty())) {
            throw new BadRequestException("Choisis une carte et un code avant d'activer la carte goofy.");
        }

        if (!normalized.isEmpty() && normalized.length() == PUBLIC_ID_RAW_LENGTH) {
            String formatted = normalized.substring(0, 5) + "-" + normalized.substring(5, 10) + "-" + normalized.substring(10, 15);
            if (instanceRepository.existsByPublicId(formatted)) {
                throw new ConflictException("Ce code est déjà attribué à une carte physique imprimée.");
            }
        }

        GoofyCardSettings settings = settingsRepository.findById(GoofyCardSettings.SINGLETON_ID)
                .orElseGet(GoofyCardSettings::new);
        settings.setId(GoofyCardSettings.SINGLETON_ID);
        settings.setCard(cardId == null ? null : cardRepository.findById(cardId)
                .orElseThrow(() -> new NotFoundException("Carte introuvable.")));
        settings.setCode(code == null || code.isBlank() ? null : code.trim());
        settings.setEnabled(enabled);
        settings.setUpdatedAt(TimeTravelContext.instantNow());
        return settingsRepository.save(settings);
    }

    /** La carte à donner si ce code est le bon, rien sinon. */
    @Transactional(readOnly = true)
    public Optional<Card> cardFor(String submittedCode) {
        GoofyCardSettings settings = settings();
        if (!settings.isEnabled() || settings.getCard() == null) {
            return Optional.empty();
        }
        String expected = normalize(settings.getCode());
        if (expected.isEmpty() || !expected.equals(normalize(submittedCode))) {
            return Optional.empty();
        }
        return Optional.of(settings.getCard());
    }

    /**
     * Marque la carte comme prise par cette personne.
     *
     * <p>L'insertion échoue si elle l'a déjà eue : c'est la clé primaire qui
     * tranche, et non une lecture préalable — deux saisies au même instant
     * passeraient toutes deux le contrôle de lecture.</p>
     */
    @Transactional
    public void markRedeemed(UUID userId) {
        if (redemptionRepository.existsById(userId)) {
            throw new ConflictException("Tu as déjà obtenu cette carte.");
        }
        redemptionRepository.saveAndFlush(new GoofyCardRedemption(userId, TimeTravelContext.instantNow()));
    }

    @Transactional(readOnly = true)
    public boolean alreadyRedeemed(UUID userId) {
        return redemptionRepository.existsById(userId);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
