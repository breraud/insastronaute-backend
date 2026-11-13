package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibrarySelection;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySelectionRepository;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySubmissionRepository;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Les medias retenus qui existent encore et restent eligibles.
 *
 * <p>Une ligne de selection n'est jamais effacee quand son media devient indisponible :
 * elle est filtree ici. Un joueur qui revient sur sa demande d'effacement retrouve ses
 * medias retenus.</p>
 */
@Component
public class MediaLibraryRetainedMedia {

    public record Retained(MediaLibrarySelection selection, ChallengeSubmission submission, String objectKey) {
    }

    private final MediaLibrarySelectionRepository selections;
    private final MediaLibrarySubmissionRepository submissions;
    private final ProofStorageService proofStorage;

    public MediaLibraryRetainedMedia(MediaLibrarySelectionRepository selections,
                                     MediaLibrarySubmissionRepository submissions,
                                     ProofStorageService proofStorage) {
        this.selections = selections;
        this.submissions = submissions;
        this.proofStorage = proofStorage;
    }

    @Transactional(readOnly = true)
    public List<Retained> available() {
        List<MediaLibrarySelection> rows = selections.findAllWithSelector();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(row -> row.getId().getSubmissionId()).distinct().toList();
        Map<UUID, ChallengeSubmission> eligible = submissions.findEligibleWithMembers(ids).stream()
                .collect(Collectors.toMap(ChallengeSubmission::getId, Function.identity(), (first, second) -> first));
        List<Retained> retained = new ArrayList<>();
        for (MediaLibrarySelection row : rows) {
            ChallengeSubmission submission = eligible.get(row.getId().getSubmissionId());
            if (submission == null) {
                continue;
            }
            List<String> keys = proofStorage.parseProofFiles(submission.getProofData());
            int index = row.getId().getMediaIndex();
            if (index < keys.size()) {
                retained.add(new Retained(row, submission, keys.get(index)));
            }
        }
        return retained;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> countByChallenge() {
        return available().stream().collect(Collectors.groupingBy(
                retained -> retained.submission().getChallenge().getId(), Collectors.counting()));
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> countByMember() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Retained retained : available()) {
            counts.merge(retained.submission().getUser().getId(), 1L, Long::sum);
            for (AppUser participant : retained.submission().getParticipants()) {
                counts.merge(participant.getId(), 1L, Long::sum);
            }
        }
        return counts;
    }
}
