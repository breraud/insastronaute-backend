package fr.insastronaute.webapi.challenge.auto.types.permanentqrscanned;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeTypeRegistry;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermanentQrScannedChallengeTypeTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PermanentQrScannedChallengeType type = new PermanentQrScannedChallengeType(userRepository);

    @Test
    void requestsOneExtraCandidateAndReturnsCursorWhenAnotherPageExists() {
        UUID afterUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID extra = UUID.fromString("00000000-0000-0000-0000-000000000004");
        when(userRepository.findScannedUserIdsAfter(afterUserId, Pageable.ofSize(3)))
                .thenReturn(List.of(first, second, extra));

        AutoChallengeCandidatePage page = type.findCandidates(
                new PermanentQrScannedParameters(), new AutoChallengeCandidateQuery(null, afterUserId, 2));

        assertThat(page.userIds()).containsExactly(first, second);
        assertThat(page.nextCursor()).isEqualTo(second);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findScannedUserIdsAfter(org.mockito.ArgumentMatchers.eq(afterUserId), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void omitsCursorWhenThereIsNoAdditionalCandidate() {
        UUID only = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(userRepository.findScannedUserIdsAfter(null, Pageable.ofSize(3))).thenReturn(List.of(only));

        AutoChallengeCandidatePage page = type.findCandidates(
                new PermanentQrScannedParameters(), new AutoChallengeCandidateQuery(null, null, 2));

        assertThat(page.userIds()).containsExactly(only);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void exposesEmptyParametersAndRejectsJsonFields() throws Exception {
        assertThat(type.key()).isEqualTo("PERMANENT_QR_SCANNED");
        assertThat(type.parameters()).isEmpty();

        ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        AutoChallengeTypeRegistry registry = new AutoChallengeTypeRegistry(List.of(type), objectMapper);
        assertThat(registry.parseAndValidate(type.key(), objectMapper.readTree("{}")))
                .isEqualTo(new PermanentQrScannedParameters());
        assertThatThrownBy(() -> registry.parseAndValidate(type.key(), objectMapper.readTree("{\"unexpected\":true}")))
                .isInstanceOf(BadRequestException.class);
    }
}
