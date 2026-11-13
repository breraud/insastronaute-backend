package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.medialibrary.dto.MediaPersonRefDto;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySubmissionRepository;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * D'un couple (soumission, position) a un fichier, seulement si la Mediatheque a le
 * droit de le montrer. Absent, hors bornes, purge ou ineligible : le meme 404, sans dire
 * lequel.
 *
 * <p>Composant a part pour que sa transaction de lecture ne s'etende pas au flux ni au
 * journal, qui ecrit.</p>
 */
@Component
public class MediaLibraryResolver {

    public record ResolvedMedia(UUID submissionId, int index, String objectKey, String contentType,
                                UUID authorId, String authorName, String challengeTitle, Instant submittedAt) {
    }

    private final MediaLibrarySubmissionRepository submissions;
    private final ProofStorageService proofStorage;

    public MediaLibraryResolver(MediaLibrarySubmissionRepository submissions, ProofStorageService proofStorage) {
        this.submissions = submissions;
        this.proofStorage = proofStorage;
    }

    @Transactional(readOnly = true)
    public ResolvedMedia resolve(UUID submissionId, int index) {
        ChallengeSubmission submission = submissions.findEligible(submissionId)
                .orElseThrow(() -> new NotFoundException(MediaLibraryStorage.MEDIA_NOT_FOUND));
        List<String> keys = proofStorage.parseProofFiles(submission.getProofData());
        if (index < 0 || index >= keys.size()) {
            throw new NotFoundException(MediaLibraryStorage.MEDIA_NOT_FOUND);
        }
        String key = keys.get(index);
        return new ResolvedMedia(submission.getId(), index, key, proofStorage.contentTypeForProofKey(key),
                submission.getUser().getId(), MediaPersonRefDto.nameOf(submission.getUser()),
                submission.getChallenge().getTitle(), submission.getCreatedAt());
    }
}
