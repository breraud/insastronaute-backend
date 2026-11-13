package fr.insastronaute.webapi.proof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
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
import fr.insastronaute.webapi.proof.dto.ProofFileUploadResponse;
import fr.insastronaute.webapi.proof.repository.ProofDraftRepository;
import fr.insastronaute.webapi.proof.repository.ProofUploadRepository;
import fr.insastronaute.webapi.storage.S3Properties;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProofStorageServiceTest {

    private final S3Presigner s3Presigner = mock(S3Presigner.class);
    private final S3Client s3Client = mock(S3Client.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ChallengeRepository challengeRepository = mock(ChallengeRepository.class);
    private final fr.insastronaute.webapi.challenge.repository.ChallengeGraceRepository graceRepository =
            mock(fr.insastronaute.webapi.challenge.repository.ChallengeGraceRepository.class);
    private final ChallengeSubmissionRepository submissionRepository = mock(ChallengeSubmissionRepository.class);
    private final ProofDraftRepository proofDraftRepository = mock(ProofDraftRepository.class);
    private final ProofUploadRepository proofUploadRepository = mock(ProofUploadRepository.class);
    private ProofStorageService proofStorageService;

    @BeforeEach
    void setUp() {
        S3Properties properties = new S3Properties();
        properties.setBucket("proofs");
        properties.setRegion("us-east-1");
        properties.setAccessKey("access");
        properties.setSecretKey("secret");
        properties.setEndpoint("http://127.0.0.1:8333");
        properties.setPublicEndpoint("https://s3.example.com");
        properties.setPresignedUrlDurationSeconds(300);

        proofStorageService = new ProofStorageService(
                s3Presigner,
                s3Client,
                properties,
                userRepository,
                challengeRepository,
                submissionRepository,
                proofDraftRepository,
                proofUploadRepository,
                graceRepository,
                new ObjectMapper(),
                15
        );
    }

    @Test
    void createDirectUploadRejectsMediaAboveFortyMegabytes() {
        UUID challengeId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        Challenge challenge = new Challenge();
        challenge.setId(challengeId);

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(user.getId()), challengeId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> proofStorageService.createDirectUpload(
                "user-sub",
                new ProofDirectUploadRequest(
                        challengeId,
                        "proof.mp4",
                        "video/mp4",
                        ProofStorageService.MAX_PROOF_SIZE_BYTES + 1
                )
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le fichier de preuve ne doit pas dépasser 40 Mo.");
    }

    @Test
    void createDirectUploadBatchRejectsWhenCurrentUserAlreadyHasPendingSubmission() {
        UUID challengeId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        Challenge challenge = new Challenge();
        challenge.setId(challengeId);
        ChallengeSubmission pendingSubmission = new ChallengeSubmission();
        pendingSubmission.setStatus("PENDING");

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(user.getId()), challengeId))
                .thenReturn(List.of(pendingSubmission));

        assertThatThrownBy(() -> proofStorageService.createDirectUploads(
                "user-sub",
                new ProofDirectUploadBatchRequest(
                        challengeId,
                        List.of(new ProofDirectUploadFileRequest("proof.png", "image/png", pngBytes().length))
                )
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vous avez déjà une soumission en attente.");
    }

    @Test
    void createDirectUploadBatchRejectsMoreThanTenFiles() {
        UUID challengeId = UUID.randomUUID();
        List<ProofDirectUploadFileRequest> files = java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> new ProofDirectUploadFileRequest(
                        "proof-" + index + ".png",
                        "image/png",
                        1
                ))
                .toList();

        assertThatThrownBy(() -> proofStorageService.createDirectUploads(
                "user-sub",
                new ProofDirectUploadBatchRequest(challengeId, files)
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Une preuve ne peut pas contenir plus de 10 fichiers.");
    }

    @Test
    void validateSubmittedProofAllowsMediaObjectKey() {
        assertThatCode(() -> proofStorageService.validateSubmittedProof(
                ProofType.MEDIA,
                "proofs/user/challenge/file.mp4"
        ))
                .doesNotThrowAnyException();
    }

    @Test
    void openProofMediaRequestsOnlyTheSelectedByteRange() throws Exception {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(10L).contentType("video/mp4").build());
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(), new ByteArrayInputStream(new byte[]{2, 3, 4, 5})
        ));

        var media = proofStorageService.openProofMedia("proofs/user/challenge/video.mp4", "bytes=2-5");

        assertThat(media.partial()).isTrue();
        assertThat(media.start()).isEqualTo(2);
        assertThat(media.end()).isEqualTo(5);
        assertThat(media.contentLength()).isEqualTo(4);
        assertThat(media.inputStream().readAllBytes()).containsExactly(2, 3, 4, 5);
        verify(s3Client).getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>argThat(
                request -> "bytes=2-5".equals(request.range()) && "proofs/user/challenge/video.mp4".equals(request.key())
        ));
    }

    @Test
    void openProofMediaRejectsAnOutOfBoundsRangeBeforeReadingS3() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(10L).contentType("video/mp4").build());

        assertThatThrownBy(() -> proofStorageService.openProofMedia("proofs/user/challenge/video.mp4", "bytes=10-20"))
                .isInstanceOf(ProofRangeNotSatisfiableException.class)
                .satisfies(exception -> assertThat(((ProofRangeNotSatisfiableException) exception).objectSize()).isEqualTo(10));
        org.mockito.Mockito.verify(s3Client, org.mockito.Mockito.never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void validateSubmittedProofRejectsMissingFileProofData() {
        assertThatThrownBy(() -> proofStorageService.validateSubmittedProof(
                ProofType.MEDIA,
                ""
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Au moins un fichier de preuve est requis.");
    }

    @Test
    void parseProofFilesReadsJsonArrayProofData() {
        assertThat(proofStorageService.parseProofFiles("""
                ["proofs/user/challenge/a.png","proofs/user/challenge/b.png"]
                """))
                .containsExactly(
                        "proofs/user/challenge/a.png",
                        "proofs/user/challenge/b.png"
                );
    }

    @Test
    void parseProofFilesKeepsLegacyLineFormatCompatibility() {
        assertThat(proofStorageService.parseProofFiles("""
                proofs/user/challenge/a.png
                proofs/user/challenge/b.png
                """))
                .containsExactly(
                        "proofs/user/challenge/a.png",
                        "proofs/user/challenge/b.png"
                );
    }

    @Test
    void createDirectUploadBatchRejectsWhenTotalDraftSizeWouldExceedLimit() {
        UUID challengeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        Challenge challenge = new Challenge();
        challenge.setId(challengeId);

        ProofDraft draft = new ProofDraft();
        draft.setUser(user);
        draft.setChallenge(challenge);
        draft.setProofType(ProofType.MEDIA);

        ProofUpload existingUpload = new ProofUpload();
        existingUpload.setDraft(draft);
        existingUpload.setProofType(ProofType.MEDIA);
        existingUpload.setSizeBytes(35L * 1024L * 1024L);

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(userId), challengeId))
                .thenReturn(List.of());
        when(proofDraftRepository.findByUserIdAndChallengeIdAndStatusForUpdate(userId, challengeId, ProofDraftStatus.DRAFT))
                .thenReturn(Optional.of(draft));
        when(proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.DRAFT))
                .thenReturn(List.of(existingUpload));
        when(proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.UPLOADING))
                .thenReturn(List.of());

        assertThatThrownBy(() -> proofStorageService.createDirectUploads(
                "user-sub",
                new ProofDirectUploadBatchRequest(
                        challengeId,
                        List.of(new ProofDirectUploadFileRequest("photo.png", "image/png", 6L * 1024L * 1024L))
                )
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le total des fichiers de preuve ne doit pas dépasser 40.00 Mio "
                        + "(reçu : 42991616 octets, soit 41.00 Mio ; limite : 41943040 octets).");
    }

    @Test
    void createDirectUploadCountsUploadingFilesAsReservedSize() {
        UUID challengeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        Challenge challenge = new Challenge();
        challenge.setId(challengeId);

        ProofDraft draft = new ProofDraft();
        draft.setUser(user);
        draft.setChallenge(challenge);
        draft.setProofType(ProofType.MEDIA);

        ProofUpload draftUpload = new ProofUpload();
        draftUpload.setDraft(draft);
        draftUpload.setProofType(ProofType.MEDIA);
        draftUpload.setSizeBytes(35L * 1024L * 1024L);

        ProofUpload uploadingUpload = new ProofUpload();
        uploadingUpload.setDraft(draft);
        uploadingUpload.setProofType(ProofType.MEDIA);
        uploadingUpload.setSizeBytes(5L * 1024L * 1024L);

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(userId), challengeId))
                .thenReturn(List.of());
        when(proofDraftRepository.findByUserIdAndChallengeIdAndStatusForUpdate(userId, challengeId, ProofDraftStatus.DRAFT))
                .thenReturn(Optional.of(draft));
        when(proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.DRAFT))
                .thenReturn(List.of(draftUpload));
        when(proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.UPLOADING))
                .thenReturn(List.of(uploadingUpload));

        ProofDirectUploadRequest request = new ProofDirectUploadRequest(
                challengeId,
                "photo.png",
                "image/png",
                1
        );

        assertThatThrownBy(() -> proofStorageService.createDirectUpload("user-sub", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le total des fichiers de preuve ne doit pas dépasser 40.00 Mio "
                        + "(reçu : 41943041 octets, soit 40.00 Mio ; limite : 41943040 octets).");
    }

    /**
     * Le budget annoncé doit compter ce que compte le contrôle qui refuse.
     *
     * <p>Une réservation en cours d'envoi occupe déjà le quota, mais n'apparaît
     * pas dans les fichiers du brouillon. Ne pas la déduire annonçait de la place
     * que la demande suivante se voyait refuser, sans qu'aucun fichier visible
     * n'explique le refus.</p>
     */
    @Test
    void draftRemainingSizeCountsFilesStillBeingUploaded() {
        UUID challengeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);

        ProofDraft draft = new ProofDraft();
        draft.setUser(user);
        draft.setProofType(ProofType.MEDIA);

        ProofUpload uploading = new ProofUpload();
        uploading.setDraft(draft);
        uploading.setProofType(ProofType.MEDIA);
        uploading.setSizeBytes(10L * 1024L * 1024L);
        uploading.setStatus(ProofUploadStatus.UPLOADING);

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(challengeRepository.existsById(challengeId)).thenReturn(true);
        when(proofDraftRepository.findByUserIdAndChallengeIdAndStatus(userId, challengeId, ProofDraftStatus.DRAFT))
                .thenReturn(Optional.of(draft));
        when(proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.DRAFT))
                .thenReturn(List.of());
        when(proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.UPLOADING))
                .thenReturn(List.of(uploading));

        var response = proofStorageService.getDraftProofFiles("user-sub", challengeId);

        // Un envoi interrompu se voit, marqué comme tel et sans aperçu : il retenait
        // ses mégaoctets sans apparaître nulle part, et la personne n'avait rien à
        // supprimer pour faire de la place.
        assertThat(response.files()).singleElement().satisfies(file -> {
            assertThat(file.status()).isEqualTo("PENDING");
            assertThat(file.previewUrl()).isNull();
        });
        assertThat(response.remainingSizeBytes()).isEqualTo(30L * 1024L * 1024L);
    }

    @Test
    void aDeadReservationIsFreedWhenItBlocksANewUpload() {
        // Une page rechargée pendant l'envoi d'une vidéo laisse une réservation que
        // rien ne termine. Elle bloquait le renvoi du même fichier jusqu'au balayage
        // suivant, pour une place que personne n'occupait.
        UUID challengeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        Challenge challenge = new Challenge();
        challenge.setId(challengeId);

        ProofUpload abandoned = new ProofUpload();
        abandoned.setSizeBytes(35L * 1024L * 1024L);
        abandoned.setStatus(ProofUploadStatus.UPLOADING);
        abandoned.setObjectKey("proofs/abandonnee.mp4");

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(userId), challengeId))
                .thenReturn(List.of());
        when(proofDraftRepository.findByUserIdAndChallengeIdAndStatusForUpdate(userId, challengeId, ProofDraftStatus.DRAFT))
                .thenReturn(Optional.empty());
        when(proofDraftRepository.saveAndFlush(any(ProofDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(proofUploadRepository.findByDraftAndStatus(any(ProofDraft.class), eq(ProofUploadStatus.DRAFT)))
                .thenReturn(List.of());
        when(proofUploadRepository.findByDraftAndStatus(any(ProofDraft.class), eq(ProofUploadStatus.UPLOADING)))
                .thenReturn(List.of(abandoned))
                .thenReturn(List.of());
        when(proofUploadRepository.findByDraftAndStatusAndCreatedAtBefore(
                any(ProofDraft.class), eq(ProofUploadStatus.UPLOADING), any()))
                .thenReturn(List.of(abandoned));
        when(proofUploadRepository.save(any(ProofUpload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        proofStorageService.createDirectUpload(
                "user-sub",
                new ProofDirectUploadRequest(challengeId, "video.mp4", "video/mp4", 10L * 1024L * 1024L)
        );

        assertThat(abandoned.getStatus()).isEqualTo(ProofUploadStatus.FAILED);
    }

    @Test
    void aReservationStillInFlightKeepsItsPlace() {
        // Le lot en cours de téléversement garde sa place : seules les réservations
        // plus vieilles que le délai d'envoi sont mortes. Sans cette borne, ajouter
        // un fichier à un envoi en cours effacerait les fichiers du même lot.
        UUID challengeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        Challenge challenge = new Challenge();
        challenge.setId(challengeId);

        ProofUpload inFlight = new ProofUpload();
        inFlight.setSizeBytes(35L * 1024L * 1024L);
        inFlight.setStatus(ProofUploadStatus.UPLOADING);
        inFlight.setObjectKey("proofs/en-vol.mp4");

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(userId), challengeId))
                .thenReturn(List.of());
        when(proofDraftRepository.findByUserIdAndChallengeIdAndStatusForUpdate(userId, challengeId, ProofDraftStatus.DRAFT))
                .thenReturn(Optional.empty());
        when(proofDraftRepository.saveAndFlush(any(ProofDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(proofUploadRepository.findByDraftAndStatus(any(ProofDraft.class), eq(ProofUploadStatus.DRAFT)))
                .thenReturn(List.of());
        when(proofUploadRepository.findByDraftAndStatus(any(ProofDraft.class), eq(ProofUploadStatus.UPLOADING)))
                .thenReturn(List.of(inFlight));
        when(proofUploadRepository.findByDraftAndStatusAndCreatedAtBefore(
                any(ProofDraft.class), eq(ProofUploadStatus.UPLOADING), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> proofStorageService.createDirectUpload(
                "user-sub",
                new ProofDirectUploadRequest(challengeId, "video.mp4", "video/mp4", 10L * 1024L * 1024L)
        )).isInstanceOf(BadRequestException.class);

        assertThat(inFlight.getStatus()).isEqualTo(ProofUploadStatus.UPLOADING);
        verify(proofUploadRepository, never()).save(any(ProofUpload.class));
    }

    @Test
    void chosenTeammatesSurviveOnTheDraft() {
        UUID challengeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID teammateId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        AppUser teammate = new AppUser();
        teammate.setId(teammateId);
        teammate.setDisplayName("Coéquipière");
        Challenge challenge = new Challenge();
        challenge.setId(challengeId);
        challenge.setMaxGroupSize(3);

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(userId), challengeId))
                .thenReturn(List.of());
        when(userRepository.findAllById(List.of(teammateId))).thenReturn(List.of(teammate));
        when(proofDraftRepository.findByUserIdAndChallengeIdAndStatusForUpdate(userId, challengeId, ProofDraftStatus.DRAFT))
                .thenReturn(Optional.empty());
        when(proofDraftRepository.saveAndFlush(any(ProofDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(proofDraftRepository.save(any(ProofDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(proofUploadRepository.findByDraftAndStatus(any(ProofDraft.class), any(ProofUploadStatus.class)))
                .thenReturn(List.of());

        var response = proofStorageService.saveDraftParticipants("user-sub", challengeId, List.of(teammateId));

        assertThat(response.participants()).extracting("id").containsExactly(teammateId);
    }

    @Test
    void aSoloChallengeRefusesTeammates() {
        UUID challengeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        Challenge challenge = new Challenge();
        challenge.setId(challengeId);
        challenge.setMaxGroupSize(1);

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(userId), challengeId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> proofStorageService.saveDraftParticipants(
                "user-sub", challengeId, List.of(UUID.randomUUID())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createDirectUploadReturnsSeaweedCompatiblePostPolicy() {
        UUID challengeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        Challenge challenge = new Challenge();
        challenge.setId(challengeId);

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(userId), challengeId))
                .thenReturn(List.of());
        when(proofDraftRepository.findByUserIdAndChallengeIdAndStatusForUpdate(userId, challengeId, ProofDraftStatus.DRAFT))
                .thenReturn(Optional.empty());
        when(proofDraftRepository.saveAndFlush(any(ProofDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(proofUploadRepository.findByDraftAndStatus(any(ProofDraft.class), any(ProofUploadStatus.class)))
                .thenReturn(List.of());
        when(proofUploadRepository.save(any(ProofUpload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProofDirectUploadResponse response = proofStorageService.createDirectUpload(
                "user-sub",
                new ProofDirectUploadRequest(challengeId, "photo.png", "image/png", pngBytes().length)
        );

        assertThat(response.uploadUrl()).isEqualTo("https://s3.example.com/proofs");
        assertThat(response.fields()).containsKeys(
                "key",
                "Content-Type",
                "policy",
                "x-amz-algorithm",
                "x-amz-credential",
                "x-amz-date",
                "x-amz-signature"
        );
        assertThat(response.fields().get("Content-Type")).isEqualTo("image/png");
        assertThat(response.expiresInSeconds()).isEqualTo(300);
        String policy = new String(Base64.getDecoder().decode(response.fields().get("policy")), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(policy).contains("[\"content-length-range\", 1, " + (pngBytes().length + 64L * 1024L) + "]");
    }

    @Test
    void createDirectUploadBatchReturnsPoliciesForAllFiles() {
        UUID challengeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        Challenge challenge = new Challenge();
        challenge.setId(challengeId);

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(userId), challengeId))
                .thenReturn(List.of());
        when(proofDraftRepository.findByUserIdAndChallengeIdAndStatusForUpdate(userId, challengeId, ProofDraftStatus.DRAFT))
                .thenReturn(Optional.empty());
        when(proofDraftRepository.saveAndFlush(any(ProofDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(proofUploadRepository.findByDraftAndStatus(any(ProofDraft.class), any(ProofUploadStatus.class)))
                .thenReturn(List.of());
        when(proofUploadRepository.save(any(ProofUpload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProofDirectUploadBatchResponse response = proofStorageService.createDirectUploads(
                "user-sub",
                new ProofDirectUploadBatchRequest(
                        challengeId,
                        List.of(
                                new ProofDirectUploadFileRequest("a.png", "image/png", 100),
                                new ProofDirectUploadFileRequest("b.jpg", "image/jpeg", 200)
                        )
                )
        );

        assertThat(response.files()).hasSize(2);
        assertThat(response.files()).allSatisfy(file -> {
            assertThat(file.uploadUrl()).isEqualTo("https://s3.example.com/proofs");
            assertThat(file.fields()).containsKeys("key", "policy", "x-amz-signature");
        });
    }

    @Test
    void completeDirectUploadValidatesStoredObjectAndMarksDraft() {
        UUID userId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        Challenge challenge = new Challenge();
        challenge.setId(challengeId);

        ProofDraft draft = new ProofDraft();
        draft.setUser(user);
        draft.setChallenge(challenge);
        draft.setProofType(ProofType.MEDIA);

        ProofUpload upload = new ProofUpload();
        upload.setId(uploadId);
        upload.setDraft(draft);
        upload.setUser(user);
        upload.setChallenge(challenge);
        upload.setObjectKey("proofs/%s/%s/image.png".formatted(userId, challengeId));
        upload.setProofType(ProofType.MEDIA);
        upload.setContentType("image/png");
        upload.setOriginalFileName("image.png");
        upload.setSizeBytes((long) pngBytes().length);
        upload.setStatus(ProofUploadStatus.UPLOADING);

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(proofUploadRepository.findByIdAndUserIdAndStatus(uploadId, userId, ProofUploadStatus.UPLOADING))
                .thenReturn(Optional.of(upload));
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength((long) pngBytes().length)
                .contentType("image/png")
                .build());
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                new ByteArrayInputStream(pngBytes())
        ));
        when(proofUploadRepository.save(any(ProofUpload.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.DRAFT)).thenReturn(List.of(upload));

        ProofFileUploadResponse response = proofStorageService.completeDirectUpload(
                "user-sub",
                uploadId
        );

        assertThat(upload.getStatus()).isEqualTo(ProofUploadStatus.DRAFT);
        assertThat(response.uploadId()).isEqualTo(uploadId);
        assertThat(response.remainingSizeBytes()).isEqualTo(ProofStorageService.MAX_PROOF_SIZE_BYTES - pngBytes().length);
    }

    @Test
    void deleteDraftProofFileAlsoClearsAnUploadStillInProgress() {
        UUID userId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        ProofDraft draft = new ProofDraft();
        draft.setUser(user);
        ProofUpload upload = new ProofUpload();
        upload.setId(uploadId);
        upload.setUser(user);
        upload.setDraft(draft);
        upload.setObjectKey("proofs/user/challenge/video.webm");
        upload.setStatus(ProofUploadStatus.UPLOADING);

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(proofUploadRepository.findByIdAndUserIdAndStatusIn(
                uploadId,
                userId,
                List.of(ProofUploadStatus.DRAFT, ProofUploadStatus.UPLOADING)
        )).thenReturn(Optional.of(upload));
        when(proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.DRAFT)).thenReturn(List.of());
        when(proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.UPLOADING)).thenReturn(List.of());

        proofStorageService.deleteDraftProofFile("user-sub", uploadId);

        verify(proofUploadRepository).delete(upload);
    }

    @Test
    void deleteDraftProofFileByObjectKeyDeletesOnlyRequestedDraftUpload() {
        UUID challengeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);

        Challenge challenge = new Challenge();
        challenge.setId(challengeId);

        ProofDraft draft = new ProofDraft();
        draft.setUser(user);
        draft.setChallenge(challenge);
        draft.setProofType(ProofType.MEDIA);

        ProofUpload upload = new ProofUpload();
        upload.setDraft(draft);
        upload.setUser(user);
        upload.setChallenge(challenge);
        upload.setObjectKey("proofs/user/challenge/image.png");
        upload.setStatus(ProofUploadStatus.DRAFT);

        when(userRepository.findByKeycloakSub("user-sub")).thenReturn(Optional.of(user));
        when(proofDraftRepository.findByUserIdAndChallengeIdAndStatus(userId, challengeId, ProofDraftStatus.DRAFT))
                .thenReturn(Optional.of(draft));
        when(proofUploadRepository.findByDraftAndObjectKeyAndStatus(draft, upload.getObjectKey(), ProofUploadStatus.DRAFT))
                .thenReturn(Optional.of(upload));
        when(proofUploadRepository.findByDraftAndStatus(draft, ProofUploadStatus.DRAFT)).thenReturn(List.of());

        proofStorageService.deleteDraftProofFile("user-sub", challengeId, upload.getObjectKey());

        verify(proofUploadRepository).delete(upload);
        verify(proofDraftRepository).save(draft);
        assertThat(draft.getProofType()).isNull();
    }

    @Test
    void deleteStaleDraftsDeletesUploadsBeforeDraft() {
        ProofDraft draft = new ProofDraft();

        ProofUpload upload = new ProofUpload();
        upload.setDraft(draft);
        upload.setObjectKey("proofs/user/challenge/image.png");

        when(proofDraftRepository.findByStatusAndCreatedAtBefore(eq(ProofDraftStatus.DRAFT), any()))
                .thenReturn(List.of(draft));
        when(proofDraftRepository.findByStatusAndCreatedAtBefore(eq(ProofDraftStatus.FAILED), any()))
                .thenReturn(List.of());
        when(proofUploadRepository.findByDraft(draft)).thenReturn(List.of(upload));

        proofStorageService.deleteStaleDrafts(Duration.ofHours(1));

        InOrder inOrder = inOrder(proofUploadRepository, proofDraftRepository);
        inOrder.verify(proofUploadRepository).deleteAll(List.of(upload));
        inOrder.verify(proofDraftRepository).delete(draft);
    }

    @Test
    void deleteExpiredUploadingProofFilesMarksUploadsFailedAndClearsInactiveDraftType() {
        UUID uploadId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();

        ProofDraft draft = new ProofDraft();
        setId(draft, draftId);

        ProofUpload upload = new ProofUpload();
        upload.setId(uploadId);
        upload.setDraft(draft);
        upload.setObjectKey("proofs/user/challenge/image.png");
        upload.setStatus(ProofUploadStatus.UPLOADING);

        when(proofUploadRepository.findByStatusAndCreatedAtBefore(any(ProofUploadStatus.class), any()))
                .thenReturn(List.of(upload));
        when(proofUploadRepository.countByDraftIdAndStatusIn(
                draftId,
                List.of(ProofUploadStatus.DRAFT, ProofUploadStatus.UPLOADING)
        )).thenReturn(0L);

        proofStorageService.deleteExpiredUploadingProofFiles(Duration.ofMinutes(15));

        verify(proofUploadRepository).updateStatus(uploadId, ProofUploadStatus.FAILED);
        verify(proofDraftRepository).clearProofType(draftId);
    }

    private static byte[] pngBytes() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
        );
    }

    private static void setId(ProofDraft draft, UUID id) {
        try {
            java.lang.reflect.Field field = ProofDraft.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(draft, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
