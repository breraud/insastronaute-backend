package fr.insastronaute.webapi.challenge.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.dto.AdminSubmissionAuditDto;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ModerationAuditServiceTest {
    @Test
    void hashesModeratorAndPersistsOnlyBoundedMetadata() {
        ModerationAuditRepository repository = mock(ModerationAuditRepository.class);
        ModerationAuditService service = new ModerationAuditService(repository, new ObjectMapper(), mock(UserRepository.class));
        UUID submissionId = UUID.randomUUID();

        service.record(submissionId, "secret-keycloak-sub", "LOCK_ACQUIRED", "SUCCESS", Map.of("source", "review"));

        ArgumentCaptor<ModerationAuditEvent> event = ArgumentCaptor.forClass(ModerationAuditEvent.class);
        verify(repository).save(event.capture());
        assertThat(event.getValue().moderatorHash()).hasSize(64).doesNotContain("secret-keycloak-sub");
        assertThat(event.getValue().metadataJson()).isEqualTo("{\"source\":\"review\"}");
    }

    /**
     * La trace de moderation est datee de l'horloge murale, meme quand la
     * requete qui la produit voyage. Sans cela, un moderateur choisirait la
     * date de sa propre trace par un en-tete, et la reprise du registre - qui
     * copie {@code created_at} dans {@code occurred_at} - propagerait ce
     * mensonge dans une table qui n'accepte aucune suppression.
     */
    @Test
    void datesTheTrailByTheWallClockEvenUnderATravelledOne() {
        Instant forced = Instant.parse("2020-01-01T00:00:00Z");
        ModerationAuditRepository repository = mock(ModerationAuditRepository.class);
        ModerationAuditService service = new ModerationAuditService(repository, new ObjectMapper(), mock(UserRepository.class));
        Instant before = Instant.now();

        TimeTravelContext.setForcedTime(forced);
        try {
            service.record(UUID.randomUUID(), "sub", "DECISION", "SUCCESS", Map.of("status", "VALIDATED"));
        } finally {
            TimeTravelContext.clear();
        }

        ArgumentCaptor<ModerationAuditEvent> event = ArgumentCaptor.forClass(ModerationAuditEvent.class);
        verify(repository).save(event.capture());
        assertThat(event.getValue().createdAt())
                .isNotEqualTo(forced)
                .isBetween(before, Instant.now());
    }

    @Test
    void rejectsSensitiveMetadataKeys() {
        ModerationAuditService service = new ModerationAuditService(mock(ModerationAuditRepository.class), new ObjectMapper(), mock(UserRepository.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.record(
                UUID.randomUUID(), "sub", "MEDIA", "SUCCESS", Map.of("proofKey", "secret")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsDecisionAndChronologicalMediaConsultationsWithResolvedNames() {
        ModerationAuditRepository repository = mock(ModerationAuditRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ModerationAuditService service = new ModerationAuditService(repository, new ObjectMapper(), userRepository);
        UUID submissionId = UUID.randomUUID();
        String moderatorSubject = "moderator-sub";
        String moderatorHash = ModerationAuditService.hashSubject(moderatorSubject);
        AppUser moderator = new AppUser();
        moderator.setKeycloakSub(moderatorSubject);
        moderator.setDisplayName("Marie Dupont");

        ModerationAuditEvent consultation = new ModerationAuditEvent(
                submissionId, moderatorHash, "MEDIA_READ", "SUCCESS",
                "{\"mediaIndex\":1,\"bytes\":2048,\"requestCount\":1}"
        );
        ModerationAuditEvent decision = new ModerationAuditEvent(
                submissionId, moderatorHash, "DECISION", "SUCCESS", "{\"status\":\"REJECTED\"}"
        );
        when(repository.findBySubmissionIdOrderByCreatedAtAsc(submissionId))
                .thenReturn(List.of(consultation, decision));
        when(userRepository.findAll()).thenReturn(List.of(moderator));

        var result = service.getSubmissionAudit(submissionId);

        assertThat(result.decision()).isNotNull();
        assertThat(result.decision().status()).isEqualTo("REJECTED");
        assertThat(result.decision().actorName()).isEqualTo("Marie Dupont");
        assertThat(result.mediaConsultations()).hasSize(1);
        assertThat(result.mediaConsultations().getFirst().mediaIndex()).isEqualTo(1);
        assertThat(result.mediaConsultations().getFirst().actorName()).isEqualTo("Marie Dupont");
        assertThat(result.mediaConsultations().getFirst().occurredAt()).isNotNull();
    }

    @Test
    void aMediaReadKeepsItsChannel() {
        ModerationAuditRepository repository = mock(ModerationAuditRepository.class);
        ModerationAuditService service = new ModerationAuditService(repository, new ObjectMapper(), mock(UserRepository.class));

        service.recordMediaRead(UUID.randomUUID(), UUID.randomUUID(), "hash", 3, 2048, 2, MediaReadChannel.MEDIA_LIBRARY);

        ArgumentCaptor<ModerationAuditEvent> event = ArgumentCaptor.forClass(ModerationAuditEvent.class);
        verify(repository).save(event.capture());
        assertThat(event.getValue().metadataJson()).contains("\"channel\":\"MEDIA_LIBRARY\"");
    }

    @Test
    void aReadWrittenBeforeChannelsExistedIsAModerationRead() {
        ModerationAuditRepository repository = mock(ModerationAuditRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ModerationAuditService service = new ModerationAuditService(repository, new ObjectMapper(), userRepository);
        UUID submissionId = UUID.randomUUID();
        String hash = ModerationAuditService.hashSubject("reader");
        when(repository.findBySubmissionIdOrderByCreatedAtAsc(submissionId)).thenReturn(List.of(
                new ModerationAuditEvent(submissionId, hash, "MEDIA_READ", "SUCCESS", "{\"mediaIndex\":0,\"bytes\":1,\"requestCount\":1}"),
                new ModerationAuditEvent(submissionId, hash, "MEDIA_READ", "SUCCESS",
                        "{\"mediaIndex\":1,\"bytes\":1,\"requestCount\":1,\"channel\":\"MEDIA_LIBRARY\"}")));
        when(userRepository.findAll()).thenReturn(List.of());

        var consultations = service.getSubmissionAudit(submissionId).mediaConsultations();

        assertThat(consultations).extracting(AdminSubmissionAuditDto.MediaConsultation::channel)
                .containsExactly("MODERATION", "MEDIA_LIBRARY");
    }
}
