package fr.insastronaute.webapi.proof.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.repository.ChallengeGraceRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.proof.domain.ProofDraft;
import fr.insastronaute.webapi.proof.domain.ProofDraftStatus;
import fr.insastronaute.webapi.proof.domain.ProofType;
import fr.insastronaute.webapi.proof.domain.ProofUpload;
import fr.insastronaute.webapi.proof.domain.ProofUploadStatus;
import fr.insastronaute.webapi.proof.dto.ProofDirectUploadBatchRequest;
import fr.insastronaute.webapi.proof.dto.ProofDirectUploadBatchResponse;
import fr.insastronaute.webapi.proof.dto.ProofDirectUploadFileRequest;
import fr.insastronaute.webapi.proof.dto.ProofDirectUploadRequest;
import fr.insastronaute.webapi.proof.dto.ProofDirectUploadResponse;
import fr.insastronaute.webapi.proof.dto.ProofDraftDto;
import fr.insastronaute.webapi.proof.dto.ProofDraftResponse;
import fr.insastronaute.webapi.proof.dto.ProofFileUploadResponse;
import fr.insastronaute.webapi.proof.repository.ProofDraftRepository;
import fr.insastronaute.webapi.proof.repository.ProofUploadRepository;
import fr.insastronaute.webapi.storage.S3Properties;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ProofStorageService {

    /** Les champs et séparateurs multipart ne font pas partie de l'objet S3 contrôlé après upload. */
    private static final long PRESIGNED_POST_MULTIPART_OVERHEAD_BYTES = 64L * 1024L;

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(ProofStorageService.class);

    public static final long MAX_PROOF_SIZE_BYTES = 40L * 1024L * 1024L;
    private static final String PROOF_OBJECT_PREFIX = "proofs/";
    private static final String FIRST_BYTES_RANGE = "bytes=0-8191";
    private static final String AWS4_ALGORITHM = "AWS4-HMAC-SHA256";
    private static final DateTimeFormatter AWS_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AWS_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);

    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "video/mp4", "mp4",
            "video/webm", "webm",
            "video/quicktime", "mov"
    );

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> VIDEO_CONTENT_TYPES = Set.of("video/mp4", "video/webm", "video/quicktime");

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final UserRepository userRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeSubmissionRepository submissionRepository;
    private final ProofDraftRepository proofDraftRepository;
    private final ProofUploadRepository proofUploadRepository;
    private final ChallengeGraceRepository graceRepository;
    private final ObjectMapper objectMapper;
    /** Au-dela, un envoi annonce mais jamais termine ne reviendra pas. */
    private final Duration uploadTimeout;
    /** Qui est entre dans l'equipe en scannant : le brouillon ne le sait pas seul. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.insastronaute.webapi.challenge.repository.ChallengeGroupInviteRepository groupInviteRepository;
    private final Tika tika = new Tika();

    public ProofStorageService(
            S3Presigner s3Presigner,
            S3Client s3Client,
            S3Properties s3Properties,
            UserRepository userRepository,
            ChallengeRepository challengeRepository,
            ChallengeSubmissionRepository submissionRepository,
            ProofDraftRepository proofDraftRepository,
            ProofUploadRepository proofUploadRepository,
            ChallengeGraceRepository graceRepository,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${app.proofs.upload-timeout-minutes}")
            long uploadTimeoutMinutes
    ) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
        this.userRepository = userRepository;
        this.challengeRepository = challengeRepository;
        this.submissionRepository = submissionRepository;
        this.proofDraftRepository = proofDraftRepository;
        this.proofUploadRepository = proofUploadRepository;
        this.graceRepository = graceRepository;
        this.objectMapper = objectMapper;
        this.uploadTimeout = Duration.ofMinutes(uploadTimeoutMinutes);
    }

    @Transactional(readOnly = true)
    public ProofDraftResponse getDraftProofFiles(String keycloakSub, UUID challengeId) {
        AppUser user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        if (!challengeRepository.existsById(challengeId)) {
            throw new NotFoundException("Défi introuvable.");
        }

        Optional<ProofDraft> draft = proofDraftRepository.findByUserIdAndChallengeIdAndStatus(
                user.getId(), challengeId, ProofDraftStatus.DRAFT);

        List<ProofUpload> uploads = draft
                .map(found -> proofUploadRepository.findByDraftAndStatus(found, ProofUploadStatus.DRAFT))
                .orElseGet(List::of);

        // Le budget restant doit compter ce que compte le contrôle qui refuse :
        // les réservations en cours d'envoi aussi. Les ignorer ici annonçait de la
        // place que la demande suivante se voyait refuser, sans qu'aucun fichier
        // visible n'explique le refus.
        List<ProofUpload> reserved = new ArrayList<>(uploads);
        draft.map(found -> proofUploadRepository.findByDraftAndStatus(found, ProofUploadStatus.UPLOADING))
                .ifPresent(reserved::addAll);

        // Et les montrer, pas seulement les compter : un envoi interrompu — une page
        // rechargée pendant la compression d'une vidéo — retenait ses mégaoctets sans
        // apparaître nulle part, et la personne n'avait rien à supprimer pour faire
        // de la place.
        List<ProofDraftDto> files = reserved.stream()
                .sorted(Comparator.comparing(ProofUpload::getCreatedAt))
                .map(this::toDraftDto)
                .toList();

        return new ProofDraftResponse(
                remainingSizeBytes(reserved), files, participantsOf(draft),
                qrJoinedUserIds(challengeId, user.getId()));
    }

    @Transactional
    public ProofDirectUploadResponse createDirectUpload(String keycloakSub, ProofDirectUploadRequest request) {
        if (request == null) {
            throw new BadRequestException("La demande d'envoi de preuve est requise.");
        }

        ProofDirectUploadBatchResponse response = createDirectUploads(keycloakSub, new ProofDirectUploadBatchRequest(
                request.challengeId(),
                List.of(new ProofDirectUploadFileRequest(
                        request.originalFileName(),
                        request.contentType(),
                        request.sizeBytes()
                ))
        ));

        return response.files().getFirst();
    }

    @Transactional
    public ProofDirectUploadBatchResponse createDirectUploads(String keycloakSub, ProofDirectUploadBatchRequest request) {
        if (request == null) {
            throw new BadRequestException("La demande d'envoi de preuve est requise.");
        }

        if (request.files() == null || request.files().isEmpty()) {
            throw new BadRequestException("Au moins un fichier de preuve est requis.");
        }
        if (request.files().size() > 10) {
            throw new BadRequestException("Une preuve ne peut pas contenir plus de 10 fichiers.");
        }

        AppUser user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        Challenge challenge = challengeRepository.findById(request.challengeId())
                .orElseThrow(() -> new NotFoundException("Défi introuvable."));

        validateUserCanPrepareProof(user, challenge);

        ProofDraft draft = getOrCreateDraft(user, challenge);
        List<ProofUpload> reservedUploads = reservedUploads(draft);

        List<DirectUploadCandidate> candidates = request.files().stream()
                .map(this::toDirectUploadCandidate)
                .toList();

        // Une réservation est comptée dès l'annonce du fichier, avant le moindre
        // octet : c'est ce qui protège le quota d'un envoi en cours. Mais une page
        // rechargée en laisse une que rien ne termine, et le balayage horaire ne la
        // rend qu'une heure plus tard — pendant laquelle le renvoi du même fichier
        // se voit refuser pour une place que personne n'occupe.
        //
        // On ne les libère qu'au moment où elles bloquent, et seulement passé le
        // délai d'envoi : un lot en cours de téléversement, lui, garde sa place.
        if (exceedsTotalSize(reservedUploads, candidates)) {
            releaseExpiredReservations(draft);
            reservedUploads = reservedUploads(draft);
        }
        validateDraftUploadBatch(draft, candidates, reservedUploads);

        if (draft.getProofType() == null) {
            draft.setProofType(ProofType.MEDIA);
            proofDraftRepository.save(draft);
        }

        List<ProofDirectUploadResponse> responses = candidates.stream()
                .map(candidate -> createDirectUploadResponse(draft, user, challenge, candidate))
                .toList();

        return new ProofDirectUploadBatchResponse(responses);
    }

    private DirectUploadCandidate toDirectUploadCandidate(ProofDirectUploadFileRequest request) {
        if (request == null) {
            throw new BadRequestException("Le fichier de preuve est requis.");
        }

        String contentType = normalizeContentType(request.contentType());
        validateAllowedMediaContentType(contentType);
        validateDeclaredSize(request.sizeBytes());

        return new DirectUploadCandidate(
                safeFileName(request.originalFileName()),
                contentType,
                request.sizeBytes()
        );
    }

    private ProofDirectUploadResponse createDirectUploadResponse(
            ProofDraft draft,
            AppUser user,
            Challenge challenge,
            DirectUploadCandidate candidate
    ) {
        String objectKey = objectKey(user.getId(), challenge.getId(), candidate.contentType());
        ProofUpload upload = new ProofUpload();
        upload.setDraft(draft);
        upload.setUser(user);
        upload.setChallenge(challenge);
        upload.setObjectKey(objectKey);
        upload.setProofType(ProofType.MEDIA);
        upload.setContentType(candidate.contentType());
        upload.setOriginalFileName(candidate.fileName());
        upload.setSizeBytes(candidate.sizeBytes());
        upload.setStatus(ProofUploadStatus.UPLOADING);
        ProofUpload savedUpload = proofUploadRepository.save(upload);

        PresignedPost presignedPost = createPostPolicy(objectKey, candidate.contentType(), candidate.sizeBytes());

        return new ProofDirectUploadResponse(
                savedUpload.getId(),
                presignedPost.url(),
                presignedPost.fields(),
                s3Properties.getPresignedUrlDurationSeconds()
        );
    }

    @Transactional
    public ProofFileUploadResponse completeDirectUpload(
            String keycloakSub,
            UUID uploadId
    ) {
        AppUser user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        ProofUpload upload = proofUploadRepository.findByIdAndUserIdAndStatus(
                uploadId,
                user.getId(),
                ProofUploadStatus.UPLOADING
        ).orElseThrow(() -> new NotFoundException("Fichier de preuve en cours d'envoi introuvable."));

        try {
            HeadObjectResponse objectMetadata = headObject(upload.getObjectKey());
            long actualSizeBytes = objectMetadata.contentLength();
            String actualContentType = normalizeContentType(objectMetadata.contentType());

            if (actualSizeBytes != upload.getSizeBytes()) {
                throw new BadRequestException("La taille du fichier S3 ne correspond pas à la taille déclarée.");
            }

            if (!actualContentType.equals(upload.getContentType())) {
                throw new BadRequestException("Le type S3 ne correspond pas au type déclaré.");
            }

            DetectedFileType detectedFileType = detectStoredFileType(upload);
            if (!detectedFileType.contentType().equals(upload.getContentType())) {
                throw new BadRequestException("Le type réel du fichier ne correspond pas au type envoyé.");
            }

            upload.setStatus(ProofUploadStatus.DRAFT);
            ProofUpload savedUpload = proofUploadRepository.save(upload);
            long remainingSizeBytes = remainingSizeBytes(proofUploadRepository.findByDraftAndStatus(
                    upload.getDraft(),
                    ProofUploadStatus.DRAFT
            ));
            return toFileUploadResponse(savedUpload, remainingSizeBytes);
        } catch (BadRequestException exception) {
            upload.setStatus(ProofUploadStatus.FAILED);
            proofUploadRepository.save(upload);
            deleteObjectQuietly(upload.getObjectKey());
            resetDraftTypeWhenNoActiveUploads(upload.getDraft());
            throw exception;
        } catch (RuntimeException exception) {
            upload.setStatus(ProofUploadStatus.FAILED);
            proofUploadRepository.save(upload);
            deleteObjectQuietly(upload.getObjectKey());
            resetDraftTypeWhenNoActiveUploads(upload.getDraft());
            throw new BadRequestException("Impossible de valider le fichier de preuve envoyé. Vous pouvez recommencer.");
        }
    }

    @Transactional
    public void deleteDraftProofFile(String keycloakSub, UUID uploadId) {
        AppUser user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        ProofUpload upload = proofUploadRepository.findByIdAndUserIdAndStatusIn(
                uploadId,
                user.getId(),
                List.of(ProofUploadStatus.DRAFT, ProofUploadStatus.UPLOADING)
        ).orElseThrow(() -> new NotFoundException("Fichier de preuve introuvable."));

        deleteDraftUpload(upload);
    }

    @Transactional
    public void deleteDraftProofFile(String keycloakSub, UUID challengeId, String objectKey) {
        AppUser user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        ProofDraft draft = proofDraftRepository.findByUserIdAndChallengeIdAndStatus(
                user.getId(),
                challengeId,
                ProofDraftStatus.DRAFT
        ).orElseThrow(() -> new NotFoundException("Brouillon de preuve introuvable."));

        ProofUpload upload = proofUploadRepository.findByDraftAndObjectKeyAndStatus(
                draft,
                objectKey,
                ProofUploadStatus.DRAFT
        ).orElseThrow(() -> new NotFoundException("Fichier de preuve introuvable."));

        deleteDraftUpload(upload);
    }

    private void deleteDraftUpload(ProofUpload upload) {
        deleteObject(upload.getObjectKey());
        ProofDraft draft = upload.getDraft();
        proofUploadRepository.delete(upload);

        List<ProofUpload> remainingUploads = proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.DRAFT);
        if (remainingUploads.isEmpty()) {
            resetDraftTypeWhenNoActiveUploads(draft);
        }
    }

    private void resetDraftTypeWhenNoActiveUploads(ProofDraft draft) {
        List<ProofUpload> draftUploads = proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.DRAFT);
        List<ProofUpload> uploadingUploads = proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.UPLOADING);

        if (draftUploads.isEmpty() && uploadingUploads.isEmpty()) {
            draft.setProofType(null);
            proofDraftRepository.save(draft);
        }
    }

    @Transactional
    public void deleteStaleDrafts(Duration retention) {
        Instant cutoff = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow().minus(retention);
        List<ProofDraft> staleDrafts = proofDraftRepository.findByStatusAndCreatedAtBefore(
                ProofDraftStatus.DRAFT,
                cutoff
        );

        for (ProofDraft draft : staleDrafts) {
            boolean allObjectsDeleted = true;
            List<ProofUpload> uploads = proofUploadRepository.findByDraft(draft);

            for (ProofUpload upload : uploads) {
                try {
                    deleteObject(upload.getObjectKey());
                } catch (RuntimeException exception) {
                    allObjectsDeleted = false;
                }
            }

            if (allObjectsDeleted) {
                deleteDraftAndUploads(draft, uploads);
            }
        }

        List<ProofDraft> failedDrafts = proofDraftRepository.findByStatusAndCreatedAtBefore(
                ProofDraftStatus.FAILED,
                cutoff
        );

        for (ProofDraft draft : failedDrafts) {
            List<ProofUpload> uploads = proofUploadRepository.findByDraft(draft);
            uploads.forEach(upload -> deleteObjectQuietly(upload.getObjectKey()));
            deleteDraftAndUploads(draft, uploads);
        }
    }

    private void deleteDraftAndUploads(ProofDraft draft, List<ProofUpload> uploads) {
        proofUploadRepository.deleteAll(uploads);
        proofDraftRepository.delete(draft);
    }

    @Transactional
    public void deleteExpiredUploadingProofFiles(Duration uploadTimeout) {
        Instant cutoff = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow().minus(uploadTimeout);
        List<ProofUpload> expiredUploads = proofUploadRepository.findByStatusAndCreatedAtBefore(
                ProofUploadStatus.UPLOADING,
                cutoff
        );

        for (ProofUpload upload : expiredUploads) {
            deleteObjectQuietly(upload.getObjectKey());
            proofUploadRepository.updateStatus(upload.getId(), ProofUploadStatus.FAILED);
            resetDraftTypeWhenNoActiveUploads(upload.getDraft().getId());
        }
    }

    public String contentTypeForProofKey(String objectKey) {
        validateProofObjectKey(objectKey);
        String key = objectKey.toLowerCase();
        if (key.endsWith(".jpg") || key.endsWith(".jpeg")) return "image/jpeg";
        if (key.endsWith(".png")) return "image/png";
        if (key.endsWith(".webp")) return "image/webp";
        if (key.endsWith(".mp4")) return "video/mp4";
        if (key.endsWith(".webm")) return "video/webm";
        if (key.endsWith(".mov")) return "video/quicktime";
        throw new BadRequestException("Type de média de preuve invalide.");
    }

    public ProofMediaStream openProofMedia(String objectKey, String rangeHeader) {
        validateProofObjectKey(objectKey);
        long size = headObject(objectKey).contentLength();
        long start = 0;
        long end = size - 1;
        boolean partial = rangeHeader != null && !rangeHeader.isBlank();
        if (partial) {
            try {
                if (!rangeHeader.startsWith("bytes=") || rangeHeader.contains(",")) throw new IllegalArgumentException();
                String[] bounds = rangeHeader.substring(6).split("-", -1);
                if (bounds.length != 2) throw new IllegalArgumentException();
                if (bounds[0].isBlank()) {
                    long suffix = Long.parseLong(bounds[1]);
                    if (suffix <= 0) throw new IllegalArgumentException();
                    start = Math.max(0, size - suffix);
                } else {
                    start = Long.parseLong(bounds[0]);
                    if (!bounds[1].isBlank()) end = Math.min(end, Long.parseLong(bounds[1]));
                }
                if (start < 0 || start >= size || end < start) throw new IllegalArgumentException();
            } catch (RuntimeException exception) {
                throw new ProofRangeNotSatisfiableException(size);
            }
        }
        GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(s3Properties.getBucket()).key(objectKey);
        if (partial) request.range("bytes=" + start + "-" + end);
        return new ProofMediaStream(
                contentTypeForProofKey(objectKey), size, start, end, partial, s3Client.getObject(request.build())
        );
    }

    /** La taille d'un fichier de preuve, lue au stockage. */
    public long proofObjectSize(String objectKey) {
        validateProofObjectKey(objectKey);
        return headObject(objectKey).contentLength();
    }

    public void validateSubmittedProof(ProofType proofType, String proofData) {
        if (proofType == ProofType.TEXT) {
            return;
        }

        List<String> objectKeys = parseProofFiles(proofData);
        if (objectKeys.isEmpty()) {
            throw new BadRequestException("Au moins un fichier de preuve est requis.");
        }

        objectKeys.forEach(this::validateProofObjectKey);
    }

    @Transactional
    public String attachSubmittedUploads(AppUser user, Challenge challenge, ProofType proofType, List<UUID> uploadIds) {
        if (proofType == ProofType.TEXT) {
            throw new BadRequestException("Les preuves texte ne doivent pas référencer de fichier.");
        }

        if (uploadIds == null || uploadIds.isEmpty()) {
            throw new BadRequestException("Au moins un fichier de preuve est requis.");
        }

        ProofDraft draft = proofDraftRepository.findByUserIdAndChallengeIdAndStatus(
                user.getId(),
                challenge.getId(),
                ProofDraftStatus.DRAFT
        ).orElseThrow(() -> new BadRequestException("Aucun brouillon de preuve n'est disponible."));

        if (draft.getProofType() != ProofType.MEDIA) {
            throw new BadRequestException("Le type du brouillon ne correspond pas au type de preuve.");
        }

        LinkedHashSet<UUID> requestedIds = uploadIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requestedIds.size() != uploadIds.size()) {
            throw new BadRequestException("Un fichier de preuve est référencé plusieurs fois.");
        }

        List<ProofUpload> uploads = proofUploadRepository.findByDraftAndIdInAndStatus(
                draft,
                List.copyOf(requestedIds),
                ProofUploadStatus.DRAFT
        );
        if (uploads.size() != requestedIds.size()) {
            throw new BadRequestException("Un fichier de preuve n'appartient pas à cette soumission.");
        }

        if (totalSize(uploads) > MAX_PROOF_SIZE_BYTES) {
            throw new BadRequestException("Le total des fichiers de preuve ne doit pas dépasser 40 Mo.");
        }

        uploads.forEach(upload -> upload.setStatus(ProofUploadStatus.ATTACHED));
        proofUploadRepository.saveAll(uploads);
        draft.setStatus(ProofDraftStatus.ATTACHED);
        proofDraftRepository.save(draft);

        return serializeProofFiles(uploads.stream()
                .sorted(Comparator.comparing(upload -> requestedIndex(requestedIds, upload.getId())))
                .map(ProofUpload::getObjectKey)
                .toList());
    }

    /**
     * Efface du stockage les fichiers d'une preuve deja soumise.
     *
     * <p>Chaque objet est retire independamment : un echec sur l'un ne doit pas empecher
     * les autres de partir, ni faire echouer la decision de moderation qui l'a declenche.
     * Un fichier qui resiste reste dans le seau sans plus etre reference — une fuite, mais
     * silencieuse et rare, la ou bloquer l'arbitrage se verrait tout de suite.</p>
     *
     * <p>Les lignes d'upload sont supprimees avec : elles ne decrivent plus rien.</p>
     *
     * @return le nombre de fichiers effectivement retires
     */
    @Transactional
    public int purgeSubmittedProof(String proofData) {
        List<String> objectKeys = parseProofFiles(proofData);
        int deleted = 0;
        for (String objectKey : objectKeys) {
            try {
                deleteObject(objectKey);
                deleted++;
            } catch (RuntimeException exception) {
                LOGGER.error("Fichier de preuve non efface [{}] : {}", objectKey, exception.getMessage());
            }
            proofUploadRepository.findByObjectKey(objectKey).ifPresent(proofUploadRepository::delete);
        }
        return deleted;
    }

    public List<String> parseProofFiles(String proofData) {
        if (proofData == null || proofData.isBlank()) {
            return List.of();
        }

        String trimmedProofData = proofData.trim();
        if (trimmedProofData.startsWith("[")) {
            try {
                return objectMapper.readValue(trimmedProofData, new TypeReference<List<String>>() {
                }).stream()
                        .filter(objectKey -> objectKey != null && !objectKey.isBlank())
                        .toList();
            } catch (JsonProcessingException exception) {
                throw new BadRequestException("Le format des fichiers de preuve est invalide.");
            }
        }

        return proofData.lines()
                .filter(line -> !line.isBlank())
                .toList();
    }

    private String serializeProofFiles(List<String> objectKeys) {
        try {
            return objectMapper.writeValueAsString(objectKeys);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Impossible de préparer les fichiers de preuve.");
        }
    }

    private ProofDraft getOrCreateDraft(AppUser user, Challenge challenge) {
        return proofDraftRepository.findByUserIdAndChallengeIdAndStatusForUpdate(
                        user.getId(),
                        challenge.getId(),
                        ProofDraftStatus.DRAFT
                )
                .orElseGet(() -> {
                    ProofDraft draft = new ProofDraft();
                    draft.setUser(user);
                    draft.setChallenge(challenge);
                    return proofDraftRepository.saveAndFlush(draft);
                });
    }

    private void resetDraftTypeWhenNoActiveUploads(UUID draftId) {
        long activeUploads = proofUploadRepository.countByDraftIdAndStatusIn(
                draftId,
                List.of(ProofUploadStatus.DRAFT, ProofUploadStatus.UPLOADING)
        );

        if (activeUploads == 0) {
            proofDraftRepository.clearProofType(draftId);
        }
    }

    /** Ce qui occupe le quota d'un brouillon : le deja depose, et le promis. */
    private List<ProofUpload> reservedUploads(ProofDraft draft) {
        List<ProofUpload> reserved =
                new ArrayList<>(proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.DRAFT));
        reserved.addAll(proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.UPLOADING));
        return reserved;
    }

    private boolean exceedsTotalSize(List<ProofUpload> reserved, List<? extends ProofFileCandidate> candidates) {
        long newFilesTotal = candidates.stream().mapToLong(ProofFileCandidate::sizeBytes).sum();
        return totalSize(reserved) + newFilesTotal > MAX_PROOF_SIZE_BYTES;
    }

    /**
     * Rendre la place des envois annonces puis abandonnes sur ce brouillon.
     *
     * <p>Le seuil est celui du balayage periodique, deliberement : un envoi en vol
     * depuis plus longtemps que le delai d'envoi ne reviendra pas, et un lot en
     * cours de televersement est trop jeune pour etre touche ici.</p>
     */
    private void releaseExpiredReservations(ProofDraft draft) {
        Instant cutoff = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow()
                .minus(uploadTimeout);
        List<ProofUpload> expired = proofUploadRepository.findByDraftAndStatusAndCreatedAtBefore(
                draft, ProofUploadStatus.UPLOADING, cutoff);

        for (ProofUpload upload : expired) {
            deleteObjectQuietly(upload.getObjectKey());
            upload.setStatus(ProofUploadStatus.FAILED);
            proofUploadRepository.save(upload);
        }

        if (!expired.isEmpty()) {
            LOGGER.info(
                    "Liberation de {} reservation(s) de preuve expirees sur le brouillon '{}'.",
                    expired.size(), draft.getId()
            );
        }
    }

    /**
     * Ajouter une personne a l'equipe en preparation d'un autre joueur.
     *
     * <p>Appele quand quelqu'un rejoint par QR avant que la preuve parte : l'equipe
     * vit dans le brouillon du chef, c'est la qu'il la voit, et c'est donc la qu'il
     * faut l'inscrire. Le chef n'a rien a faire -- il montre son code.</p>
     */
    @Transactional
    public void addDraftParticipant(AppUser submitter, Challenge challenge, AppUser participant) {
        ProofDraft draft = getOrCreateDraft(submitter, challenge);
        Set<AppUser> participants = new java.util.LinkedHashSet<>(draft.getParticipants());
        participants.add(participant);
        draft.setParticipants(participants);
        proofDraftRepository.save(draft);
    }

    /**
     * Enregistrer les coequipiers choisis pour un defi de groupe.
     *
     * <p>Ils vivaient dans la page seule : un rafraichissement les perdait, quand
     * les fichiers deja deposes, eux, survivaient cote serveur.</p>
     */
    @Transactional
    public ProofDraftResponse saveDraftParticipants(
            String keycloakSub, UUID challengeId, List<UUID> participantUserIds
    ) {
        AppUser user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Défi introuvable."));

        validateUserCanPrepareProof(user, challenge);

        int maxGroupSize = challenge.getMaxGroupSize() == null ? 1 : challenge.getMaxGroupSize();
        if (maxGroupSize <= 1) {
            throw new BadRequestException("Ce défi ne se joue pas en groupe.");
        }

        List<UUID> requested = participantUserIds == null ? List.of() : participantUserIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(id -> !id.equals(user.getId()))
                .distinct()
                .toList();
        if (requested.size() > maxGroupSize - 1) {
            throw new BadRequestException("Ce défi n'accepte pas autant de coéquipiers.");
        }

        List<AppUser> participants = userRepository.findAllById(requested);
        if (participants.size() != requested.size()) {
            throw new NotFoundException("Coéquipier introuvable.");
        }

        // Le brouillon nait ici quand la composition precede le depot : c'est le
        // meme brouillon que celui des fichiers, et le menage l'emporte de la meme
        // facon s'il reste vide.
        ProofDraft draft = getOrCreateDraft(user, challenge);
        draft.setParticipants(new java.util.LinkedHashSet<>(participants));
        proofDraftRepository.save(draft);

        List<ProofUpload> reserved = reservedUploads(draft);
        List<ProofDraftDto> files = reserved.stream()
                .sorted(Comparator.comparing(ProofUpload::getCreatedAt))
                .map(this::toDraftDto)
                .toList();
        return new ProofDraftResponse(
                remainingSizeBytes(reserved), files, participantsOf(Optional.of(draft)),
                qrJoinedUserIds(challengeId, user.getId()));
    }

    private void validateDraftUploadBatch(
            ProofDraft draft,
            List<? extends ProofFileCandidate> candidates,
            List<ProofUpload> existingUploads
    ) {
        long currentTotal = totalSize(existingUploads);
        long newFilesTotal = candidates.stream().mapToLong(ProofFileCandidate::sizeBytes).sum();

        if (currentTotal + newFilesTotal > MAX_PROOF_SIZE_BYTES) {
            throw new BadRequestException(totalSizeLimitExceededMessage(currentTotal + newFilesTotal));
        }

        if (draft.getProofType() != null && draft.getProofType() != ProofType.MEDIA) {
            throw new BadRequestException("Le type du brouillon ne correspond pas au type de preuve.");
        }
    }

    private void validateDeclaredSize(long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new BadRequestException("La taille du fichier de preuve est requise.");
        }

        if (sizeBytes > MAX_PROOF_SIZE_BYTES) {
            throw new BadRequestException("Le fichier de preuve ne doit pas dépasser 40 Mo.");
        }
    }

    private void validateAllowedMediaContentType(String contentType) {
        if (!IMAGE_CONTENT_TYPES.contains(contentType) && !VIDEO_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Type de fichier non autorisé.");
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new BadRequestException("Type de fichier non autorisé.");
        }

        return switch (contentType.toLowerCase()) {
            case "image/jpg", "image/pjpeg" -> "image/jpeg";
            default -> contentType.toLowerCase();
        };
    }

    private long totalSize(List<ProofUpload> uploads) {
        return uploads.stream()
                .mapToLong(ProofUpload::getSizeBytes)
                .sum();
    }

    private long remainingSizeBytes(List<ProofUpload> uploads) {
        return Math.max(0, MAX_PROOF_SIZE_BYTES - totalSize(uploads));
    }

    private String totalSizeLimitExceededMessage(long totalSizeBytes) {
        return String.format(
                Locale.ROOT,
                "Le total des fichiers de preuve ne doit pas dépasser %.2f Mio "
                        + "(reçu : %d octets, soit %.2f Mio ; limite : %d octets).",
                mebibytes(MAX_PROOF_SIZE_BYTES),
                totalSizeBytes,
                mebibytes(totalSizeBytes),
                MAX_PROOF_SIZE_BYTES
        );
    }

    private double mebibytes(long sizeBytes) {
        return sizeBytes / (1024d * 1024d);
    }

    private int requestedIndex(LinkedHashSet<UUID> requestedIds, UUID uploadId) {
        int index = 0;
        for (UUID requestedId : requestedIds) {
            if (requestedId.equals(uploadId)) {
                return index;
            }
            index++;
        }
        return Integer.MAX_VALUE;
    }

    private void validateUserCanPrepareProof(AppUser user, Challenge challenge) {
        validateChallengeActive(challenge, user);
        validateMembersCanAccessChallenge(user, Set.of(), challenge);
        validateMembersCompletedPrerequisites(user, Set.of(), challenge);
        validatePromoChallengeOpen(user, challenge);

        List<ChallengeSubmission> history = submissionRepository.findBlockingSubmissionsForUsersAndChallenge(
                List.of(user.getId()),
                challenge.getId()
        );

        if (history.stream().anyMatch(submission -> "PENDING".equals(submission.getStatus()))) {
            throw new BadRequestException("Vous avez déjà une soumission en attente.");
        }

        if (history.stream().anyMatch(submission -> "VALIDATED".equals(submission.getStatus()))) {
            throw new BadRequestException("Vous avez déjà validé ce défi.");
        }
    }

    /**
     * Un defi de promo est ferme des la premiere preuve envoyee.
     *
     * <p>Refuser au depot plutot qu'a l'envoi evite de laisser quelqu'un televerser
     * une video pour un defi que sa promo a deja releve.</p>
     *
     * <p>Doit rester identique a ChallengeService.validatePromoChallengeOpen : les
     * deux gardent le meme geste par deux chemins.</p>
     */
    private void validatePromoChallengeOpen(AppUser user, Challenge challenge) {
        if (!challenge.isPromo()) {
            return;
        }

        UUID departmentId = user.getDepartment();
        if (departmentId == null) {
            throw new BadRequestException(
                    "Ce défi se relève pour un département, et le tien n'est pas renseigné.");
        }

        List<ChallengeSubmission> promoSubmissions =
                submissionRepository.findPromoBlockingSubmissions(challenge.getId(), departmentId);

        if (promoSubmissions.stream().anyMatch(submission -> "VALIDATED".equals(submission.getStatus()))) {
            throw new ConflictException("Ta promo a déjà validé ce défi.");
        }
        if (!promoSubmissions.isEmpty()) {
            throw new ConflictException("Ta promo a déjà envoyé une preuve pour ce défi : elle attend d'être jugée.");
        }
    }

    private void validateChallengeActive(Challenge challenge, AppUser user) {
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();

        // Doit rester identique à ChallengeService.validateChallengeActive : les deux
        // gardent le même geste par deux chemins, et une divergence rouvrirait l'un
        // des deux à ce que l'autre refuse.
        if (challenge.getPublishedAt() != null && challenge.getPublishedAt().isAfter(now)) {
            throw new BadRequestException("Ce défi n'est pas encore disponible.");
        }

        if (challenge.getStartDate() != null && challenge.getStartDate().isAfter(now)) {
            throw new BadRequestException("Ce défi n'est pas encore disponible.");
        }

        if (challenge.getEndDate() != null && challenge.getEndDate().isBefore(now)) {
            // La grâce vaut ici comme à la soumission. Sans cela elle ne servirait
            // à rien sur un défi à preuve média : le joueur aurait le droit de
            // soumettre, et pas celui de déposer le moindre fichier.
            boolean graced = !graceRepository
                    .findActiveForUsersAndChallenge(List.of(user.getId()), challenge.getId(), now)
                    .isEmpty();
            if (!graced) {
                throw new BadRequestException("Ce défi n'est plus disponible.");
            }
        }
    }

    private void validateMembersCompletedPrerequisites(AppUser user, Set<AppUser> participants, Challenge challenge) {
        if (challenge.getPrerequisiteChallenges().isEmpty()) {
            return;
        }

        List<AppUser> members = new ArrayList<>();
        members.add(user);
        members.addAll(participants);

        for (AppUser member : members) {
            List<String> missing = challenge.getPrerequisiteChallenges().stream()
                    .filter(prerequisite -> !submissionRepository.existsValidatedSubmissionForUserAndChallenge(
                            member.getId(), prerequisite.getId()))
                    .map(Challenge::getTitle)
                    .filter(title -> title != null && !title.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            if (missing.isEmpty()) {
                continue;
            }
            // Dire ce qu'il manque, pas seulement qu'il manque quelque chose :
            // « prérequis incomplets » n'indiquait ni quoi ni à qui.
            String titles = missing.stream()
                    .map(title -> "« " + title + " »")
                    .collect(Collectors.joining(", "));
            String message = member.getId().equals(user.getId())
                    ? "Il te reste à valider " + titles + " avant ce défi."
                    : memberLabel(member) + " n'a pas encore validé " + titles + ".";
            throw new BadRequestException(message, "participantUserIds", message);
        }
    }

    private static String memberLabel(AppUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : "Un membre du groupe";
    }

    private void validateMembersCanAccessChallenge(AppUser user, Set<AppUser> participants, Challenge challenge) {
        if (challenge.getTargetDepartments().isEmpty()) {
            return;
        }

        List<AppUser> members = new ArrayList<>();
        members.add(user);
        members.addAll(participants);

        boolean allMembersAllowed = members.stream()
                .map(AppUser::getDepartment)
                .allMatch(departmentId -> departmentId != null && challenge.getTargetDepartments().stream()
                        .anyMatch(department -> department.getId().equals(departmentId)));

        if (!allMembersAllowed) {
            throw new BadRequestException("Défi non autorisé pour un ou plusieurs membres du groupe.");
        }
    }

    private PresignedPost createPostPolicy(
            String objectKey,
            String contentType,
            long sizeBytes
    ) {
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        String shortDate = AWS_DATE_FORMATTER.format(now);
        String dateTime = AWS_DATE_TIME_FORMATTER.format(now);
        String region = nonBlankOrDefault(s3Properties.getRegion(), "eu-fr");
        String credential = "%s/%s/%s/s3/aws4_request".formatted(
                nonBlankOrDefault(s3Properties.getAccessKey(), "unused"),
                shortDate,
                region
        );

        String policy = """
                {
                  "expiration": "%s",
                  "conditions": [
                    {"bucket": "%s"},
                    {"key": "%s"},
                    {"Content-Type": "%s"},
                    ["content-length-range", 1, %d],
                    {"x-amz-algorithm": "%s"},
                    {"x-amz-credential": "%s"},
                    {"x-amz-date": "%s"}
                  ]
                }
                """.formatted(
                now.plus(signatureDuration()),
                escapeJson(s3Properties.getBucket()),
                escapeJson(objectKey),
                escapeJson(contentType),
                sizeBytes + PRESIGNED_POST_MULTIPART_OVERHEAD_BYTES,
                AWS4_ALGORITHM,
                escapeJson(credential),
                dateTime
        );

        String encodedPolicy = Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8));
        String signature = signature(encodedPolicy, shortDate, region);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", objectKey);
        fields.put("Content-Type", contentType);
        fields.put("policy", encodedPolicy);
        fields.put("x-amz-algorithm", AWS4_ALGORITHM);
        fields.put("x-amz-credential", credential);
        fields.put("x-amz-date", dateTime);
        fields.put("x-amz-signature", signature);

        return new PresignedPost(postActionUrl(), fields);
    }

    private HeadObjectResponse headObject(String objectKey) {
        return s3Client.headObject(HeadObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .build());
    }

    private DetectedFileType detectStoredFileType(ProofUpload upload) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(upload.getObjectKey())
                .range(FIRST_BYTES_RANGE)
                .build();

        try (ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(getObjectRequest)) {
            return new DetectedFileType(normalizeContentType(tika.detect(inputStream, upload.getOriginalFileName())));
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de lire le fichier de preuve envoyé.");
        }
    }

    private String postActionUrl() {
        String endpoint = nonBlankOrDefault(s3Properties.getPublicEndpoint(), s3Properties.getEndpoint());
        String bucket = urlEncodePathSegment(s3Properties.getBucket());

        if (endpoint == null || endpoint.isBlank()) {
            return "https://s3.%s.amazonaws.com/%s".formatted(nonBlankOrDefault(s3Properties.getRegion(), "eu-fr"), bucket);
        }

        URI uri = URI.create(endpoint);
        String path = uri.getRawPath();
        String normalizedPath = path == null || path.isBlank() || "/".equals(path)
                ? "/" + bucket
                : path.replaceAll("/+$", "") + "/" + bucket;

        return URI.create("%s://%s%s".formatted(uri.getScheme(), uri.getRawAuthority(), normalizedPath)).toString();
    }

    private String signature(String encodedPolicy, String shortDate, String region) {
        byte[] dateKey = hmac(("AWS4" + nonBlankOrDefault(s3Properties.getSecretKey(), "unused")).getBytes(StandardCharsets.UTF_8), shortDate);
        byte[] dateRegionKey = hmac(dateKey, region);
        byte[] dateRegionServiceKey = hmac(dateRegionKey, "s3");
        byte[] signingKey = hmac(dateRegionServiceKey, "aws4_request");
        return hex(hmac(signingKey, encodedPolicy));
    }

    private byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new BadRequestException("Impossible de préparer la signature S3.");
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append("%02x".formatted(value));
        }
        return builder.toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String urlEncodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String nonBlankOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private ProofFileUploadResponse toFileUploadResponse(ProofUpload upload, long remainingSizeBytes) {
        return new ProofFileUploadResponse(
                upload.getId(),
                remainingSizeBytes
        );
    }

    private ProofDraftDto toDraftDto(ProofUpload upload) {
        // Une réservation n'a pas d'objet complet dans le stockage : en signer un
        // aperçu donnerait un lien mort. Son statut le dit, et la page s'en sert
        // pour la présenter comme un envoi interrompu plutôt qu'une preuve prête.
        boolean ready = upload.getStatus() == ProofUploadStatus.DRAFT;
        return new ProofDraftDto(
                upload.getId(),
                upload.getOriginalFileName(),
                upload.getContentType(),
                upload.getSizeBytes(),
                ready ? "READY" : "PENDING",
                upload.getCreatedAt(),
                ready ? createReadUrlString(upload.getObjectKey()) : null
        );
    }

    /** Ceux qui sont entres en scannant le code de ce joueur sur ce defi. */
    private List<UUID> qrJoinedUserIds(UUID challengeId, UUID submitterId) {
        if (groupInviteRepository == null) {
            return List.of();
        }
        return groupInviteRepository.findByChallengeIdAndSubmitterId(challengeId, submitterId)
                .map(invite -> List.copyOf(invite.getJoinedUserIds()))
                .orElseGet(List::of);
    }

    private List<fr.insastronaute.webapi.friend.dto.FriendUserDto> participantsOf(Optional<ProofDraft> draft) {
        return draft.map(found -> found.getParticipants().stream()
                        .map(fr.insastronaute.webapi.friend.dto.FriendUserDto::from)
                        .toList())
                .orElseGet(List::of);
    }

    private String createReadUrlString(String objectKey) {
        validateProofObjectKey(objectKey);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(signatureDuration())
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private String objectKey(UUID userId, UUID challengeId, String contentType) {
        return "proofs/%s/%s/%s.%s".formatted(
                userId,
                challengeId,
                UUID.randomUUID(),
                EXTENSIONS_BY_CONTENT_TYPE.get(contentType)
        );
    }

    private void validateProofObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BadRequestException("La clé de preuve S3 est requise.");
        }

        if (!objectKey.startsWith(PROOF_OBJECT_PREFIX) || objectKey.contains("..")) {
            throw new BadRequestException("La clé de preuve S3 est invalide.");
        }
    }

    private void deleteObject(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .build());
    }

    private void deleteObjectQuietly(String objectKey) {
        try {
            deleteObject(objectKey);
        } catch (RuntimeException ignored) {
            // Cleanup must not hide the original upload failure.
        }
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "proof";
        }

        String normalized = fileName.replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');

        if (lastSlash >= 0) {
            normalized = normalized.substring(lastSlash + 1);
        }

        return normalized.isBlank() ? "proof" : normalized;
    }

    private Duration signatureDuration() {
        return Duration.ofSeconds(s3Properties.getPresignedUrlDurationSeconds());
    }

    private interface ProofFileCandidate {
        long sizeBytes();
    }

    private record DirectUploadCandidate(
            String fileName,
            String contentType,
            long sizeBytes
    ) implements ProofFileCandidate {
    }

    private record PresignedPost(String url, Map<String, String> fields) {
    }

    private record DetectedFileType(String contentType) {
    }
}
