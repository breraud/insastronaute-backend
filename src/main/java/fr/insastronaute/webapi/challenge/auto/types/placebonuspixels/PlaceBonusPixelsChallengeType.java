package fr.insastronaute.webapi.challenge.auto.types.placebonuspixels;

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
 * "Spend N bonus pixels on r/place". The canvas keeps no per-player pixel
 * history, so total pixels placed is not knowable; bonus pixels are, because
 * they are earned through challenges and counted on the user.
 */
@Component
public final class PlaceBonusPixelsChallengeType implements AutoChallengeType<PlaceBonusPixelsParameters> {

    private final UserRepository userRepository;

    public PlaceBonusPixelsChallengeType(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public String key() {
        return "PLACE_BONUS_PIXELS_USED";
    }

    @Override
    public String publicName() {
        return "Utiliser des pixels bonus sur r/place";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur a consommé un nombre donné de pixels bonus gagnés par les défis.";
    }

    @Override
    public Class<PlaceBonusPixelsParameters> parametersType() {
        return PlaceBonusPixelsParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("count", "Nombre de pixels bonus", 1, 1_000_000));
    }

    @Override
    public String phrase(PlaceBonusPixelsParameters parameters) {
        return parameters.count() == 1
                ? "Utilise un pixel bonus sur r/place"
                : "Utilise " + parameters.count() + " pixels bonus sur r/place";
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.PLACE_PIXEL_PLACED);
    }

    @Override
    public AutoChallengeProgress progress(PlaceBonusPixelsParameters parameters, UUID userId) {
        long used = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable."))
                .getPlaceBonusPixelsUsed();
        return AutoChallengeProgress.of(used, parameters.count());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(PlaceBonusPixelsParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = userRepository.findUsersWithBonusPixelsUsed(
                parameters.count(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit())
        );
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
