package fr.insastronaute.webapi.challenge.auto.types.permanentqrscanned;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class PermanentQrScannedChallengeType implements AutoChallengeType<PermanentQrScannedParameters> {

    private final UserRepository userRepository;

    public PermanentQrScannedChallengeType(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public String key() {
        return "PERMANENT_QR_SCANNED";
    }

    @Override
    public String publicName() {
        return "QR permanent scanne";
    }

    @Override
    public String description() {
        return "Valide le defi lorsque le QR permanent est scanne.";
    }

    @Override
    public Class<PermanentQrScannedParameters> parametersType() {
        return PermanentQrScannedParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of();
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(
            PermanentQrScannedParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> candidates = userRepository.findScannedUserIdsAfter(query.afterUserId(), Pageable.ofSize(query.limit() + 1));
        boolean hasNextPage = candidates.size() > query.limit();
        List<UUID> userIds = hasNextPage ? List.copyOf(candidates.subList(0, query.limit())) : List.copyOf(candidates);
        UUID nextCursor = hasNextPage ? userIds.getLast() : null;
        return new AutoChallengeCandidatePage(userIds, nextCursor);
    }

}
