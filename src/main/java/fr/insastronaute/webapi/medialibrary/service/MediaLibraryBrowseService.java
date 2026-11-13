package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.common.web.PageResponse;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibrarySelection;
import fr.insastronaute.webapi.medialibrary.domain.MediaSelectionKey;
import fr.insastronaute.webapi.medialibrary.dto.MediaChallengeDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaItemDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaPersonDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaPersonRefDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaSelectionMarkDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaSubmissionDto;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySelectionRepository;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySubmissionRepository;
import fr.insastronaute.webapi.medialibrary.repository.PersonCountRow;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Les trois vues de la Mediatheque : par defi, par personne, et la grille d'une soumission. */
@Service
public class MediaLibraryBrowseService {

    public static final int SUBMISSION_PAGE_SIZE = 24;
    public static final int PEOPLE_PAGE_SIZE = 50;

    private final MediaLibraryAccess access;
    private final MediaLibrarySubmissionRepository submissions;
    private final MediaLibrarySelectionRepository selections;
    private final MediaSizeLookup sizes;
    private final MediaLibraryRetainedMedia retained;
    private final ProofStorageService proofStorage;
    private final UserRepository users;

    public MediaLibraryBrowseService(MediaLibraryAccess access, MediaLibrarySubmissionRepository submissions,
                                     MediaLibrarySelectionRepository selections, MediaSizeLookup sizes,
                                     MediaLibraryRetainedMedia retained, ProofStorageService proofStorage,
                                     UserRepository users) {
        this.access = access;
        this.submissions = submissions;
        this.selections = selections;
        this.sizes = sizes;
        this.retained = retained;
        this.proofStorage = proofStorage;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<MediaChallengeDto> challenges(String keycloakSub, Collection<String> authorities, MediaLibraryFilter filter) {
        access.require(keycloakSub, authorities);
        Map<UUID, Long> retainedByChallenge = retained.countByChallenge();
        return submissions.countByChallenge(filter.statuses(), filter.type()).stream()
                .map(row -> new MediaChallengeDto(row.challengeId(), row.title(), row.category(),
                        row.submissionCount(), retainedByChallenge.getOrDefault(row.challengeId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<MediaPersonDto> people(String keycloakSub, Collection<String> authorities,
                                               MediaLibraryFilter filter, String query, int page) {
        access.require(keycloakSub, authorities);
        Map<UUID, Long> counts = new HashMap<>();
        for (PersonCountRow row : submissions.countByAuthor(filter.statuses(), filter.type())) {
            counts.merge(row.userId(), row.submissionCount(), Long::sum);
        }
        for (PersonCountRow row : submissions.countByParticipant(filter.statuses(), filter.type())) {
            counts.merge(row.userId(), row.submissionCount(), Long::sum);
        }
        Map<UUID, Long> retainedByMember = retained.countByMember();
        String needle = normalize(query);
        List<MediaPersonDto> people = users.findAllById(counts.keySet()).stream()
                .map(user -> new MediaPersonDto(user.getId(), MediaPersonRefDto.nameOf(user),
                        counts.get(user.getId()), retainedByMember.getOrDefault(user.getId(), 0L)))
                .filter(person -> needle.isEmpty() || normalize(person.displayName()).contains(needle))
                .sorted(Comparator.comparingLong(MediaPersonDto::submissionCount).reversed()
                        .thenComparing(MediaPersonDto::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int safePage = Math.max(0, page);
        // En long : une page absurde (page * 50 au-dela d'un int) doit donner une page vide.
        int from = (int) Math.min((long) safePage * PEOPLE_PAGE_SIZE, people.size());
        int to = Math.min(from + PEOPLE_PAGE_SIZE, people.size());
        return PageResponse.from(new PageImpl<>(people.subList(from, to), PageRequest.of(safePage, PEOPLE_PAGE_SIZE), people.size()));
    }

    @Transactional(readOnly = true)
    public PageResponse<MediaSubmissionDto> submissions(String keycloakSub, Collection<String> authorities,
                                                        MediaLibraryFilter filter, UUID challengeId, UUID userId,
                                                        boolean selectedOnly, int page) {
        access.require(keycloakSub, authorities);
        // Dans un defi on lit la chronologie ; ailleurs, defi par defi.
        Sort sort = challengeId != null ? Sort.by("createdAt", "id") : Sort.by("challenge.title", "createdAt", "id");
        Page<ChallengeSubmission> found = submissions.search(filter.statuses(), filter.type(), challengeId, userId,
                selectedOnly, PageRequest.of(Math.max(0, page), SUBMISSION_PAGE_SIZE, sort));

        // search() ne fait qu'un join simple : challenge, auteur et participants restent LAZY.
        // Une seule requete, jointures comprises, recharge les membres de toute la page.
        List<ChallengeSubmission> content = found.getContent();
        Map<UUID, ChallengeSubmission> withMembers = content.isEmpty() ? Map.of()
                : submissions.findEligibleWithMembers(content.stream().map(ChallengeSubmission::getId).toList()).stream()
                        .collect(Collectors.toMap(ChallengeSubmission::getId, Function.identity()));

        Map<UUID, List<String>> keysBySubmission = new HashMap<>();
        Set<String> allKeys = new HashSet<>();
        for (ChallengeSubmission submission : content) {
            List<String> keys = proofStorage.parseProofFiles(submission.getProofData());
            keysBySubmission.put(submission.getId(), keys);
            allKeys.addAll(keys);
        }
        Map<MediaSelectionKey, MediaLibrarySelection> marks = found.isEmpty() ? Map.of()
                : selections.findBySubmissionIds(keysBySubmission.keySet()).stream()
                        .collect(Collectors.toMap(MediaLibrarySelection::getId, Function.identity()));
        Map<String, Long> knownSizes = sizes.sizesOf(allKeys);

        return PageResponse.from(found.map(submission -> {
            // Redevenue inéligible entre les deux requêtes : on retombe sur la ligne de la page.
            ChallengeSubmission resolved = withMembers.getOrDefault(submission.getId(), submission);
            return toDto(resolved, keysBySubmission.get(submission.getId()), marks, knownSizes);
        }));
    }

    private MediaSubmissionDto toDto(ChallengeSubmission submission, List<String> keys,
                                     Map<MediaSelectionKey, MediaLibrarySelection> marks, Map<String, Long> knownSizes) {
        List<MediaItemDto> media = new ArrayList<>(keys.size());
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            MediaLibrarySelection mark = marks.get(new MediaSelectionKey(submission.getId(), index));
            media.add(new MediaItemDto(index, safeContentType(proofStorage, key), knownSizes.getOrDefault(key, 0L),
                    mark == null ? null : new MediaSelectionMarkDto(MediaPersonRefDto.of(mark.getSelectedBy()), mark.getSelectedAt())));
        }
        List<MediaPersonRefDto> participants = submission.getParticipants().stream()
                .map(MediaPersonRefDto::of)
                .sorted(Comparator.comparing(MediaPersonRefDto::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new MediaSubmissionDto(submission.getId(), submission.getChallenge().getId(),
                submission.getChallenge().getTitle(), submission.getStatus(), submission.getCreatedAt(),
                MediaPersonRefDto.of(submission.getUser()), participants, media);
    }

    /** Une cle ancienne au type illisible ne doit pas faire tomber toute la page. */
    public static String safeContentType(ProofStorageService proofStorage, String key) {
        try {
            return proofStorage.contentTypeForProofKey(key);
        } catch (RuntimeException exception) {
            return "application/octet-stream";
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).trim();
    }
}
