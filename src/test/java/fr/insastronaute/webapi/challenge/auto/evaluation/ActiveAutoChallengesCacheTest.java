package fr.insastronaute.webapi.challenge.auto.evaluation;

import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L'instantané est partagé par tous les joueurs pendant sa péremption : il ne
 * peut pas dépendre de la date qu'un membre du staff s'est choisie.
 */
class ActiveAutoChallengesCacheTest {

    @AfterEach
    void clearTimeTravel() {
        TimeTravelContext.clear();
    }

    @Test
    void snapshotsOnTheRealClockEvenUnderTimeTravel() {
        ChallengeRepository repository = mock(ChallengeRepository.class);
        when(repository.findActiveAutomaticChallenges(any(), any())).thenReturn(List.of());
        ActiveAutoChallengesCache cache = new ActiveAutoChallengesCache(repository, 10_000, () -> 0L);

        TimeTravelContext.setForcedTime(Instant.now().plus(Duration.ofDays(30)));
        Instant before = Instant.now();
        cache.current();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findActiveAutomaticChallenges(now.capture(), any());
        assertThat(now.getValue()).isBetween(before, after);
    }
}
