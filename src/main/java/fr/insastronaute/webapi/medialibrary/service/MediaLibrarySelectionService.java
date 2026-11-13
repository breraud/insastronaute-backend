package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.medialibrary.dto.MediaRefDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaSelectionPatchDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaSelectionSummaryDto;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySelectionRepository;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySubmissionRepository;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La selection, unique et commune : chacun peut retirer ce qu'un autre a retenu.
 */
@Service
public class MediaLibrarySelectionService {

    static final String UNAVAILABLE = "MEDIA_LIBRARY_MEDIA_UNAVAILABLE";

    // Memes valeurs que ChallengeService.purgeChallengeProofs, qui ne purge que les
    // soumissions tranchees.
    private static final String STATUS_VALIDATED = "VALIDATED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final MediaLibraryAccess access;
    private final MediaLibrarySelectionRepository selections;
    private final MediaLibrarySubmissionRepository submissions;
    private final ProofStorageService proofStorage;
    private final MediaLibraryRetainedMedia retained;
    private final MediaSizeLookup sizes;
    private final UserRepository users;

    public MediaLibrarySelectionService(MediaLibraryAccess access, MediaLibrarySelectionRepository selections,
                                        MediaLibrarySubmissionRepository submissions, ProofStorageService proofStorage,
                                        MediaLibraryRetainedMedia retained, MediaSizeLookup sizes, UserRepository users) {
        this.access = access;
        this.selections = selections;
        this.submissions = submissions;
        this.proofStorage = proofStorage;
        this.retained = retained;
        this.sizes = sizes;
        this.users = users;
    }

    @Transactional
    public void patch(String keycloakSub, Collection<String> authorities, MediaSelectionPatchDto patch) {
        access.require(keycloakSub, authorities);
        AppUser actor = users.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        List<MediaRefDto> adds = patch.add() == null ? List.of() : patch.add();
        List<MediaRefDto> removes = patch.remove() == null ? List.of() : patch.remove();
        // Tout ou rien : la verification precede toute ecriture.
        requireAvailable(adds);
        removes.forEach(ref -> selections.deleteOne(ref.submissionId(), ref.index()));
        Instant now = Instant.now();
        adds.forEach(ref -> selections.insertIfAbsent(ref.submissionId(), ref.index(), actor.getId(), now));
    }

    @Transactional(readOnly = true)
    public MediaSelectionSummaryDto summary(String keycloakSub, Collection<String> authorities) {
        access.require(keycloakSub, authorities);
        List<MediaLibraryRetainedMedia.Retained> available = retained.available();
        Set<String> keys = available.stream().map(MediaLibraryRetainedMedia.Retained::objectKey).collect(Collectors.toSet());
        Map<String, Long> knownSizes = sizes.sizesOf(keys);
        long totalBytes = available.stream().mapToLong(item -> knownSizes.getOrDefault(item.objectKey(), 0L)).sum();
        long unavailable = Math.max(0, selections.count() - available.size());
        return new MediaSelectionSummaryDto(available.size(), totalBytes, unavailable);
    }

    /**
     * Combien de medias retenus et disponibles la purge de ces defis detruirait.
     *
     * <p>Ne compte que les medias des soumissions tranchees (VALIDATED ou REJECTED) :
     * ce sont les seules que {@code ChallengeService.purgeChallengeProofs} purge. Les
     * soumissions PENDING ou WAITING_CONFIRMATION ne perdent rien a cette purge, et
     * les compter grossirait l'avertissement sans raison.</p>
     */
    @Transactional(readOnly = true)
    public long retainedInChallenges(Collection<UUID> challengeIds) {
        Set<UUID> wanted = new HashSet<>(challengeIds);
        return retained.available().stream()
                .filter(item -> wanted.contains(item.submission().getChallenge().getId()))
                .filter(item -> isDecided(item.submission()))
                .count();
    }

    private static boolean isDecided(ChallengeSubmission submission) {
        String status = submission.getStatus();
        return STATUS_VALIDATED.equals(status) || STATUS_REJECTED.equals(status);
    }

    private void requireAvailable(List<MediaRefDto> adds) {
        if (adds.isEmpty()) {
            return;
        }
        Set<UUID> ids = adds.stream().map(MediaRefDto::submissionId).collect(Collectors.toSet());
        Map<UUID, ChallengeSubmission> eligible = submissions.findEligibleWithMembers(ids).stream()
                .collect(Collectors.toMap(ChallengeSubmission::getId, Function.identity(), (first, second) -> first));
        for (MediaRefDto ref : adds) {
            ChallengeSubmission submission = eligible.get(ref.submissionId());
            if (submission == null || ref.index() < 0
                    || ref.index() >= proofStorage.parseProofFiles(submission.getProofData()).size()) {
                throw new CodedBadRequestException(UNAVAILABLE, "Un des médias n'est plus disponible : rien n'a été retenu.");
            }
        }
    }
}
