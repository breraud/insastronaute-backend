package fr.insastronaute.webapi.challenge.auto.types.qrscanned;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Scan the permanent QR link". There is one such link, and scanning it flips a
 * flag on the user, so this is a yes/no phrase rather than a counter.
 */
@Component
public final class QrScannedChallengeType implements AutoChallengeType<QrScannedParameters> {

    private final UserRepository userRepository;

    public QrScannedChallengeType(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public String key() {
        return "QR_SCANNED";
    }

    @Override
    public String publicName() {
        return "Scanner le QR permanent";
    }

    @Override
    public String description() {
        return "Se déclenche au premier scan du QR permanent. Sans paramètre : il n'existe qu'un seul QR.";
    }

    @Override
    public Class<QrScannedParameters> parametersType() {
        return QrScannedParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of();
    }

    @Override
    public String phrase(QrScannedParameters parameters) {
        return "Scanne le QR code INSAstronaute";
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.QR_SCANNED);
    }

    @Override
    public AutoChallengeProgress progress(QrScannedParameters parameters, UUID userId) {
        boolean scanned = Boolean.TRUE.equals(userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable."))
                .getScanned());
        return AutoChallengeProgress.of(scanned ? 1 : 0, 1);
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(QrScannedParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = userRepository.findUsersWhoScanned(query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit()));
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
