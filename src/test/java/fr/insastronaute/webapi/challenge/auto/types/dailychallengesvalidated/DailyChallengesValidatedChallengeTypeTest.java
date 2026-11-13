package fr.insastronaute.webapi.challenge.auto.types.dailychallengesvalidated;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeTypeRegistry;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyChallengesValidatedChallengeTypeTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CHALLENGE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock ChallengeSubmissionRepository submissionRepository;
    private DailyChallengesValidatedChallengeType type;

    @BeforeEach void setUp() {
        type = new DailyChallengesValidatedChallengeType(submissionRepository);
    }

    @Test void keepsItsStableKey() {
        // Les défis enregistrés portent cette clé : la changer les rendrait inévaluables.
        assertThat(type.key()).isEqualTo("DAILY_CHALLENGES_VALIDATED");
    }

    @Test void progressCountsOnlyDailyChallenges() {
        when(submissionRepository.countValidatedDailyChallengesForUser(PLAYER)).thenReturn(2L);

        var progress = type.progress(new DailyChallengesValidatedParameters(3), PLAYER);

        assertThat(progress.current()).isEqualTo(2);
        assertThat(progress.target()).isEqualTo(3);
        assertThat(progress.completed()).isFalse();
    }

    @Test void progressCompletesOnceTheCountIsReached() {
        when(submissionRepository.countValidatedDailyChallengesForUser(PLAYER)).thenReturn(3L);

        assertThat(type.progress(new DailyChallengesValidatedParameters(3), PLAYER).completed()).isTrue();
    }

    @Test void reactsToEveryValidation() {
        // Sans ce déclencheur, rien ne tombe avant le balayage horaire.
        assertThat(type.triggers()).contains(AutoChallengeTrigger.CHALLENGE_VALIDATED);
    }

    @Test void aShortPageEndsTheScan() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        when(submissionRepository.findUsersWithValidatedDailyChallenges(4, CHALLENGE, null, PageRequest.of(0, 2)))
                .thenReturn(List.of(first));

        var page = type.findCandidates(new DailyChallengesValidatedParameters(4), new AutoChallengeCandidateQuery(CHALLENGE, null, 2));

        assertThat(page.userIds()).containsExactly(first);
        assertThat(page.nextCursor()).isNull();
    }

    @Test void aFullPageResumesAfterItsLastPlayer() {
        UUID cursor = UUID.fromString("00000000-0000-0000-0000-000000000009");
        UUID first = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID second = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        when(submissionRepository.findUsersWithValidatedDailyChallenges(4, CHALLENGE, cursor, PageRequest.of(0, 2)))
                .thenReturn(List.of(first, second));

        var page = type.findCandidates(new DailyChallengesValidatedParameters(4), new AutoChallengeCandidateQuery(CHALLENGE, cursor, 2));

        assertThat(page.userIds()).containsExactly(first, second);
        assertThat(page.nextCursor()).isEqualTo(second);
    }

    @Test void phraseNamesTheCount() {
        assertThat(type.phrase(new DailyChallengesValidatedParameters(1))).isEqualTo("Fais valider un défi du jour");
        assertThat(type.phrase(new DailyChallengesValidatedParameters(5))).isEqualTo("Fais valider 5 défis du jour");
    }

    // Par le registre, comme le moteur : c'est le JSON enregistré qui arrive ici.
    @Test void aMissingCountMeansOne() throws Exception {
        assertThat(registry().parseAndValidate(type.key(), MAPPER.readTree("{}")))
                .isEqualTo(new DailyChallengesValidatedParameters(1));
    }

    @Test void rejectsACountBelowOne() {
        assertThatThrownBy(() -> registry().parseAndValidate(type.key(), MAPPER.readTree("{\"count\":0}")))
                .isInstanceOf(BadRequestException.class);
    }

    private AutoChallengeTypeRegistry registry() {
        return new AutoChallengeTypeRegistry(List.of(type), MAPPER);
    }
}
