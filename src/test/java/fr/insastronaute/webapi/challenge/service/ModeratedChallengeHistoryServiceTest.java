package fr.insastronaute.webapi.challenge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.dto.ModerationHistoryType;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionEvent;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModeratedChallengeHistoryServiceTest {
    private final ModeratedChallengeHistoryService service = new ModeratedChallengeHistoryService(
            mock(AdminActionEventRepository.class), mock(UserRepository.class), new ObjectMapper());

    @Test
    void mapsARejectedDecisionAndItsMessage() {
        UUID submissionId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser actor = user("actor", "Camille");
        AppUser player = user("player", "Alex");
        AdminActionEvent event = new AdminActionEvent(
                Instant.parse("2026-09-08T12:30:00Z"), actor, player, AdminActionType.CHALLENGE_REJECTED,
                """
                {"challengeTitle":"Photo","points":150,"comment":"Date absente",\
                "submissionId":"%s","challengeId":"%s"}
                """.formatted(submissionId, challengeId), false);

        var dto = service.toDto(event, true, null);

        assertThat(dto.type()).isEqualTo(ModerationHistoryType.REJECTED);
        assertThat(dto.actor().displayName()).isEqualTo("Camille");
        assertThat(dto.player().displayName()).isEqualTo("Alex");
        assertThat(dto.challengeTitle()).isEqualTo("Photo");
        assertThat(dto.points()).isEqualTo(150);
        assertThat(dto.message()).isEqualTo("Date absente");
        assertThat(dto.submissionId()).isEqualTo(submissionId);
        assertThat(dto.challengeId()).isEqualTo(challengeId);
    }

    @Test
    void hidesAnotherModeratorButKeepsEveryOtherField() {
        AppUser actor = user("actor", "Camille");
        AppUser requester = user("requester", "Morgan");
        AdminActionEvent event = new AdminActionEvent(
                Instant.now(), actor, user("player", "Alex"), AdminActionType.CHALLENGE_VALIDATED,
                "{\"challengeTitle\":\"Photo\",\"points\":10}", false);

        var dto = service.toDto(event, false, requester);

        assertThat(dto.actor()).isNull();
        assertThat(dto.actorHidden()).isTrue();
        assertThat(dto.challengeTitle()).isEqualTo("Photo");
    }

    @Test
    void keepsTheCurrentModeratorsOwnIdentityVisible() {
        AppUser actor = user("actor", "Camille");
        AdminActionEvent event = new AdminActionEvent(
                Instant.now(), actor, user("player", "Alex"), AdminActionType.CHALLENGE_GRANTED,
                "{\"challengeTitle\":\"Stand\"}", false);

        var dto = service.toDto(event, false, actor);

        assertThat(dto.actor().displayName()).isEqualTo("Camille");
        assertThat(dto.actorHidden()).isFalse();
    }

    @Test
    void distinguishesAnUnknownHistoricalActorFromAHiddenActor() {
        AdminActionEvent event = new AdminActionEvent(
                Instant.now(), null, user("player", "Alex"), AdminActionType.CHALLENGE_VALIDATED,
                "{}", true);

        var dto = service.toDto(event, false, user("requester", "Morgan"));

        assertThat(dto.actor()).isNull();
        assertThat(dto.actorHidden()).isFalse();
        assertThat(dto.backfilled()).isTrue();
    }

    /**
     * Une ligne d'historique survit a son defi, et doit le dire.
     *
     * <p>Le titre, les points et les participants sont dans son propre JSON : elle
     * reste lisible sans le defi, et c'est ce qu'on veut. Mais l'ecran s'appuie sur
     * les identifiants pour proposer une grace ou un ajustement de bonus -- deux
     * gestes qui partiraient en 404 sans ce drapeau.</p>
     */
    @Test
    void saysWhenTheChallengeBehindALineIsGone() {
        AdminActionEventRepository events = mock(AdminActionEventRepository.class);
        ChallengeRepository challenges = mock(ChallengeRepository.class);
        ChallengeSubmissionRepository submissions = mock(ChallengeSubmissionRepository.class);
        ModeratedChallengeHistoryService history = new ModeratedChallengeHistoryService(
                events, mock(UserRepository.class), new ObjectMapper(), challenges, submissions);

        UUID goneChallenge = UUID.randomUUID();
        UUID goneSubmission = UUID.randomUUID();
        UUID liveChallenge = UUID.randomUUID();
        UUID liveSubmission = UUID.randomUUID();
        AdminActionEvent deleted = new AdminActionEvent(
                Instant.now(), user("actor", "Camille"), user("player", "Alex"),
                AdminActionType.CHALLENGE_VALIDATED,
                """
                {"challengeTitle":"Photo","points":150,"submissionId":"%s","challengeId":"%s"}
                """.formatted(goneSubmission, goneChallenge), false);
        AdminActionEvent alive = new AdminActionEvent(
                Instant.now(), user("actor", "Camille"), user("player", "Alex"),
                AdminActionType.CHALLENGE_VALIDATED,
                """
                {"challengeTitle":"Selfie","points":10,"submissionId":"%s","challengeId":"%s"}
                """.formatted(liveSubmission, liveChallenge), false);

        // Une seule requete pour toute la page, et seuls les vivants en reviennent.
        when(challenges.findExistingIds(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(liveChallenge));
        when(submissions.findExistingIds(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(liveSubmission));
        when(events.searchModeratedChallengesDesc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(java.util.List.of(deleted, alive)));

        var result = history.search("admin", java.util.List.of("ROLE_ADMIN"), null, null,
                null, null, null, null, Sort.Direction.DESC, 0, 25);

        assertThat(result.content()).hasSize(2);
        // La ligne reste, et reste lisible sans le defi : c'est tout le point.
        assertThat(result.content().getFirst().challengeTitle()).isEqualTo("Photo");
        assertThat(result.content().getFirst().points()).isEqualTo(150);
        assertThat(result.content().getFirst().challengeDeleted()).isTrue();
        assertThat(result.content().getFirst().submissionDeleted()).isTrue();
        assertThat(result.content().getLast().challengeDeleted()).isFalse();
        assertThat(result.content().getLast().submissionDeleted()).isFalse();
    }

    /** Sans de quoi trancher, on ne declare rien supprime plutot que tout. */
    @Test
    void saysNothingIsDeletedWhenItCannotTell() {
        AdminActionEventRepository events = mock(AdminActionEventRepository.class);
        ModeratedChallengeHistoryService history = new ModeratedChallengeHistoryService(
                events, mock(UserRepository.class), new ObjectMapper());
        AdminActionEvent event = new AdminActionEvent(
                Instant.now(), user("actor", "Camille"), user("player", "Alex"),
                AdminActionType.CHALLENGE_VALIDATED,
                """
                {"challengeId":"%s"}
                """.formatted(UUID.randomUUID()), false);
        when(events.searchModeratedChallengesDesc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(java.util.List.of(event)));

        var result = history.search("admin", java.util.List.of("ROLE_ADMIN"), null, null,
                null, null, null, null, Sort.Direction.DESC, 0, 25);

        assertThat(result.content().getFirst().challengeDeleted()).isFalse();
    }

    @Test
    void challengeLeadCanFilterAndSeeAnotherDecisionAuthor() {
        AdminActionEventRepository events = mock(AdminActionEventRepository.class);
        ModeratedChallengeHistoryService history = new ModeratedChallengeHistoryService(
                events, mock(UserRepository.class), new ObjectMapper());
        UUID actorId = UUID.randomUUID();
        AppUser actor = user("actor", "Camille");
        ReflectionTestUtils.setField(actor, "id", actorId);
        AdminActionEvent event = new AdminActionEvent(
                Instant.now(), actor, user("player", "Alex"), AdminActionType.CHALLENGE_VALIDATED,
                "{}", false);
        when(events.searchModeratedChallengesDesc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(actorId),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(java.util.List.of(event)));

        var result = history.search("lead", java.util.List.of("ROLE_RESPOS_DEFIS"), null, actorId,
                null, null, null, null, Sort.Direction.DESC, 0, 25);

        assertThat(result.content()).singleElement().satisfies(row -> {
            assertThat(row.actor().displayName()).isEqualTo("Camille");
            assertThat(row.actorHidden()).isFalse();
        });
    }

    @Test
    void searchesChallengesWithoutDependingOnTheDetailsJsonFormatting() {
        AdminActionEventRepository events = mock(AdminActionEventRepository.class);
        ModeratedChallengeHistoryService history = new ModeratedChallengeHistoryService(
                events, mock(UserRepository.class), new ObjectMapper());
        when(events.searchModeratedChallengesDesc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("%code!_secret!%%"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        history.search("admin", java.util.List.of("ROLE_ADMIN"), null, null,
                " Code_Secret% ", null, null, null, Sort.Direction.DESC, 0, 25);

        verify(events).searchModeratedChallengesDesc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("%code!_secret!%%"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deputyChallengeLeadCanSeeAnotherDecisionAuthor() {
        AdminActionEventRepository events = mock(AdminActionEventRepository.class);
        ModeratedChallengeHistoryService history = new ModeratedChallengeHistoryService(
                events, mock(UserRepository.class), new ObjectMapper());
        AdminActionEvent event = new AdminActionEvent(
                Instant.now(), user("actor", "Camille"), user("player", "Alex"),
                AdminActionType.CHALLENGE_VALIDATED, "{}", false);
        when(events.searchModeratedChallengesDesc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(java.util.List.of(event)));

        var result = history.search("deputy", java.util.List.of("ROLE_SOUS_RESPO_DEFIS"), null, null,
                null, null, null, null, Sort.Direction.DESC, 0, 25);

        assertThat(result.content().getFirst().actor().displayName()).isEqualTo("Camille");
    }

    private static AppUser user(String subject, String name) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setKeycloakSub(subject);
        user.setDisplayName(name);
        return user;
    }
}
