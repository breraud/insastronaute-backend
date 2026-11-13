package fr.insastronaute.webapi.challenge.service;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.challenge.audit.MediaReadAuditPublisher;
import fr.insastronaute.webapi.challenge.audit.ModerationAuditService;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeTypeRegistry;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.repository.ChallengeCategoryRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeFavoriteRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionMessageRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.department.repository.DepartmentVerificationLinkRepository;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceJournalTest {

    @Mock ChallengeRepository challengeRepository;
    @Mock ChallengeCategoryRepository categoryRepository;
    @Mock ChallengeFavoriteRepository favoriteRepository;
    @Mock UserRepository userRepository;
    @Mock ChallengeSubmissionRepository submissionRepository;
    @Mock ChallengeSubmissionMessageRepository submissionMessageRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock DepartmentVerificationLinkRepository verificationLinkRepository;
    @Mock ProofStorageService proofStorageService;
    @Mock NotificationService notificationService;
    @Mock AutoChallengeTypeRegistry autoChallengeTypeRegistry;
    @Mock ModerationAuditService moderationAuditService;
    @Mock MediaReadAuditPublisher mediaReadAuditPublisher;
    @Mock AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    @Mock AdminActionJournal journal;

    private ChallengeService challengeService;
    private AppUser player;
    private Challenge challenge;
    private ChallengeSubmission submission;

    @BeforeEach
    void setUp() {
        var challengeVisibility = org.mockito.Mockito.mock(ChallengeVisibilityService.class);
        var friendshipRepository = org.mockito.Mockito.mock(fr.insastronaute.webapi.friend.repository.FriendshipRepository.class);
        challengeService = new ChallengeService(
                challengeRepository,
                categoryRepository,
                favoriteRepository,
                userRepository,
                submissionRepository,
                submissionMessageRepository,
                departmentRepository,
                verificationLinkRepository,
                proofStorageService,
                notificationService,
                autoChallengeTypeRegistry,
                moderationAuditService,
                mediaReadAuditPublisher,
                autoChallengeTriggerPublisher,
                journal,
                challengeVisibility,
                friendshipRepository,
                org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class)
        );

        player = new AppUser();
        player.setId(UUID.randomUUID());
        player.setKeycloakSub("player-sub");

        challenge = new Challenge();
        challenge.setId(UUID.randomUUID());
        challenge.setTitle("Bullseye");
        challenge.setPointsReward(10);

        submission = new ChallengeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setStatus("PENDING");
        submission.setUser(player);
        submission.setChallenge(challenge);
        // La decision exige que le moderateur detienne le verrou de review sur
        // cette soumission, sans quoi decideSubmission refuse le jugement.
        submission.setReviewLockedBy("sub-moderator");
        submission.setReviewStartedAt(Instant.now());
        submission.setReviewExpiresAt(Instant.now().plusSeconds(900));
    }

    /**
     * L'ajustement d'apres coup a son propre geste au registre.
     *
     * <p>Avant lui, accorder un bonus oublie demandait de refuser la preuve pour la
     * revalider : le registre portait alors un refus qui n'a jamais eu lieu, et le
     * joueur recevait la notification qui va avec.</p>
     */
    @Test
    void adjustingBonusPointsAfterValidationWritesItsOwnEventWithBeforeAndAfter() {
        submission.setStatus("VALIDATED");
        submission.setBonusPoints(10);
        when(submissionRepository.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        challengeService.adjustValidatedSubmissionBonusPoints(submission.getId(), "sub-moderator",
                List.of("ROLE_ADMIN"), 30, "Photo exceptionnelle.");

        assertThat(submission.getBonusPoints()).isEqualTo(30);
        assertThat(submission.getStatus()).isEqualTo("VALIDATED");
        verify(journal).record(eq("sub-moderator"), eq(player.getId()),
                eq(AdminActionType.CHALLENGE_BONUS_ADJUSTED), details.capture());
        assertThat(details.getValue()).containsEntry("bonusPoints", 30);
        // L'avant, sans quoi la ligne dit « +30 » sans dire si c'est un cadeau ou
        // la moitie de ce qui avait ete promis.
        assertThat(details.getValue()).containsEntry("previousBonusPoints", 10);
        assertThat(details.getValue()).containsEntry("comment", "Photo exceptionnelle.");
    }

    @Test
    void anAdjustmentTellsThePlayerWhatChangedAndWhy() {
        submission.setStatus("VALIDATED");
        submission.setBonusPoints(0);
        when(submissionRepository.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));

        challengeService.adjustValidatedSubmissionBonusPoints(submission.getId(), "sub-moderator",
                List.of("ROLE_ADMIN"), 15, "Bonus oublié à la validation.");

        // Un score qui bouge sans explication se lit comme un bug.
        verify(notificationService).notifyUser(
                eq(player.getId()), any(), eq(NotificationType.CHALLENGE_BONUS_ADJUSTED),
                any(), org.mockito.ArgumentMatchers.contains("Bonus oublié à la validation."),
                any(), eq("CHALLENGE_SUBMISSION"), eq(submission.getId()));
    }

    @Test
    void onlyAValidatedSubmissionCanSeeItsPointsAdjusted() {
        submission.setStatus("PENDING");
        when(submissionRepository.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.adjustValidatedSubmissionBonusPoints(
                submission.getId(), "sub-moderator", List.of("ROLE_ADMIN"), 30, "Parce que."))
                .isInstanceOf(fr.insastronaute.webapi.common.web.ConflictException.class);
        verify(journal, org.mockito.Mockito.never()).record(any(), any(), any(), any());
    }

    @Test
    void anAdjustmentWithoutAReasonIsRefused() {
        // Le motif est ce qui rend le geste relisable : sans lui, un ecart de points
        // reste inexplicable pour l'encadrement comme pour le joueur.
        submission.setStatus("VALIDATED");
        when(submissionRepository.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.adjustValidatedSubmissionBonusPoints(
                submission.getId(), "sub-moderator", List.of("ROLE_ADMIN"), 30, "   "))
                .isInstanceOf(fr.insastronaute.webapi.common.web.BadRequestException.class);
    }

    @Test
    void anAdjustmentCannotMakeTheRewardNegative() {
        submission.setStatus("VALIDATED");
        when(submissionRepository.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));

        // Le defi rapporte 10 : retirer 11 rendrait la recompense finale negative.
        assertThatThrownBy(() -> challengeService.adjustValidatedSubmissionBonusPoints(
                submission.getId(), "sub-moderator", List.of("ROLE_ADMIN"), -11, "Erreur de saisie."))
                .isInstanceOf(fr.insastronaute.webapi.common.web.BadRequestException.class);
        assertThat(submission.getBonusPoints()).isZero();
    }

    @Test
    void validatingASubmissionWritesAValidatedEventNamingTheModerator() {
        when(submissionRepository.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));
        // Map<String, Object> (et non Map<String, ?>) : le type joker empeche
        // javac de verifier containsEntry contre une valeur concrete.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        challengeService.decideSubmission(submission.getId(), "sub-moderator",
                List.of("ROLE_ADMIN"), "VALIDATED", "beau boulot", null);

        verify(journal).record(eq("sub-moderator"), eq(player.getId()),
                eq(AdminActionType.CHALLENGE_VALIDATED), details.capture());
        assertThat(details.getValue()).containsEntry("challengeTitle", "Bullseye");
        assertThat(details.getValue()).containsEntry("points", 10);
    }

    /**
     * Les points bonus sont la part discretionnaire de la decision : sans eux
     * la ligne ne dit pas pourquoi le joueur est soudain plus riche que ce que
     * le defi rapporte.
     */
    @Test
    void aValidationCarriesTheBonusPointsTheModeratorChose() {
        when(submissionRepository.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        challengeService.decideSubmission(submission.getId(), "sub-moderator",
                List.of("ROLE_ADMIN"), "VALIDATED", "beau boulot", 25);

        verify(journal).record(eq("sub-moderator"), eq(player.getId()),
                eq(AdminActionType.CHALLENGE_VALIDATED), details.capture());
        assertThat(details.getValue()).containsEntry("bonusPoints", 25);
    }

    @Test
    void rejectingASubmissionWritesARejectedEvent() {
        when(submissionRepository.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        challengeService.decideSubmission(submission.getId(), "sub-moderator",
                List.of("ROLE_ADMIN"), "REJECTED", "preuve illisible", null);

        verify(journal).record(eq("sub-moderator"), eq(player.getId()),
                eq(AdminActionType.CHALLENGE_REJECTED), details.capture());
        // Un rejet ne donne rien : pas de points bonus a expliquer.
        assertThat(details.getValue()).doesNotContainKey("bonusPoints");
    }

    @Test
    void aManualGrantWritesAGrantedEventNamingTheAdmin() {
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challenge.getId())).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(
                List.of(player.getId()), challenge.getId())).thenReturn(List.of());
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(player));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        challengeService.createApprovedManualSubmission(
                player.getId(), challenge.getId(), "vu sur place", "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.CHALLENGE_GRANTED), details.capture());
        // Le commentaire est la seule chose qui dise pourquoi ce defi a ete
        // accorde sans preuve.
        assertThat(details.getValue()).containsEntry("comment", "vu sur place");
        assertThat(details.getValue()).containsEntry("challengeTitle", "Bullseye");
    }

    /**
     * Un defi de groupe valide pour quatre joueurs en credite quatre, et la
     * ligne ne nomme que celui qui a soumis : sans la composition, filtrer sur
     * un des trois autres ne trouve rien.
     */
    @Test
    void validatingAGroupSubmissionCarriesTheWholeComposition() {
        AppUser mate = new AppUser();
        mate.setId(UUID.randomUUID());
        mate.setDisplayName("Coequipiere");
        player.setDisplayName("Soumettrice");
        submission.setParticipants(new java.util.LinkedHashSet<>(List.of(mate)));
        when(submissionRepository.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        challengeService.decideSubmission(submission.getId(), "sub-moderator",
                List.of("ROLE_ADMIN"), "VALIDATED", null, null);

        verify(journal).record(eq("sub-moderator"), eq(player.getId()),
                eq(AdminActionType.CHALLENGE_VALIDATED), details.capture());
        // getParticipants() ne contient pas l'auteur - resolveParticipants l'en
        // retire - donc la composition doit le remettre en tete.
        assertThat(details.getValue())
                .containsEntry("participantNames", List.of("Soumettrice", "Coequipiere"))
                .containsEntry("participantIds",
                        List.of(player.getId().toString(), mate.getId().toString()));
    }

    /** Un rejet touche le groupe entier tout autant qu'une validation. */
    @Test
    void rejectingAGroupSubmissionCarriesTheWholeComposition() {
        AppUser mate = new AppUser();
        mate.setId(UUID.randomUUID());
        mate.setDisplayName("Coequipiere");
        player.setDisplayName("Soumettrice");
        submission.setParticipants(new java.util.LinkedHashSet<>(List.of(mate)));
        when(submissionRepository.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        challengeService.decideSubmission(submission.getId(), "sub-moderator",
                List.of("ROLE_ADMIN"), "REJECTED", "preuve illisible", null);

        verify(journal).record(eq("sub-moderator"), eq(player.getId()),
                eq(AdminActionType.CHALLENGE_REJECTED), details.capture());
        assertThat(details.getValue())
                .containsEntry("participantIds",
                        List.of(player.getId().toString(), mate.getId().toString()));
    }

    /**
     * Une soumission solo n'a pas de composition : les deux cles manquent
     * plutot que de valoir une liste d'un seul nom, meme traitement que le
     * commentaire.
     */
    @Test
    void validatingASoloSubmissionOmitsBothCompositionKeys() {
        when(submissionRepository.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        challengeService.decideSubmission(submission.getId(), "sub-moderator",
                List.of("ROLE_ADMIN"), "VALIDATED", null, null);

        verify(journal).record(eq("sub-moderator"), eq(player.getId()),
                eq(AdminActionType.CHALLENGE_VALIDATED), details.capture());
        assertThat(details.getValue())
                .doesNotContainKey("participantNames")
                .doesNotContainKey("participantIds");
    }

    /**
     * La validation manuelle n'accorde aujourd'hui qu'a un joueur seul : la
     * composition y est donc toujours absente. Ce test fixe ce comportement
     * actuel - il passe identiquement que l'appel a putGroupComposition existe
     * ou non, donc il ne garantit rien sur le jour ou ce chemin accepterait un
     * groupe.
     */
    @Test
    void aManualGrantOmitsBothCompositionKeys() {
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challenge.getId())).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(
                List.of(player.getId()), challenge.getId())).thenReturn(List.of());
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(player));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        challengeService.createApprovedManualSubmission(
                player.getId(), challenge.getId(), "vu sur place", "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.CHALLENGE_GRANTED), details.capture());
        assertThat(details.getValue())
                .doesNotContainKey("participantNames")
                .doesNotContainKey("participantIds");
    }

    /** Rien n'oblige a commenter : la cle manque alors, elle ne vaut pas null. */
    @Test
    void aManualGrantWithoutACommentOmitsTheKey() {
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challenge.getId())).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(
                List.of(player.getId()), challenge.getId())).thenReturn(List.of());
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(player));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        challengeService.createApprovedManualSubmission(
                player.getId(), challenge.getId(), "   ", "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.CHALLENGE_GRANTED), details.capture());
        assertThat(details.getValue()).doesNotContainKey("comment");
    }
}
