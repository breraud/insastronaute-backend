package fr.insastronaute.webapi.challenge.auto.types.alldailycompleted;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllDailyCompletedChallengeTypeTest {
    private static final LocalDate START_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 8, 9);
    // Le 9 août à minuit, heure de Paris : le dernier jour de la période commence.
    private static final Instant LAST_DAY = Instant.parse("2026-08-08T22:00:00Z");
    private static final Instant START_INCLUSIVE = Instant.parse("2026-07-31T22:00:00Z");
    private static final Instant END_EXCLUSIVE = Instant.parse("2026-08-09T22:00:00Z");
    private static final String KEY = "ALL_DAILY_CHALLENGES_COMPLETED";
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SUMMARY = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Mock ChallengeRepository challengeRepository;

    private AllDailyCompletedChallengeType typeAt(Instant now) {
        return new AllDailyCompletedChallengeType(challengeRepository, () -> now);
    }

    @Test void staysSilentBeforeTheLastDayOfThePeriod() {
        // Avant, des défis du jour de la période peuvent ne pas encore exister :
        // qui aurait fait ceux du lundi toucherait le bilan de la semaine.
        var type = typeAt(LAST_DAY.minusMillis(1));

        assertThat(type.findCandidates(parameters(), new AutoChallengeCandidateQuery(SUMMARY, null, 10)).userIds()).isEmpty();
        assertThat(type.progress(parameters(), PLAYER)).isNull();
        verifyNoInteractions(challengeRepository);
    }

    @Test void scansTheWholePeriodFromTheStartOfItsLastDay() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(challengeRepository.findUsersCompletingAllDailyChallenges(
                eq(START_INCLUSIVE), eq(END_EXCLUSIVE), eq(KEY), eq(SUMMARY), eq(null), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        var page = typeAt(LAST_DAY).findCandidates(parameters(), new AutoChallengeCandidateQuery(SUMMARY, null, 1));

        assertThat(page.userIds()).containsExactly(first);
        assertThat(page.nextCursor()).isEqualTo(first);
    }

    @Test void aOneDayPeriodIsLiveFromItsMidnight() {
        // Le cas du « défi du jour » : évalué pendant sa journée, pas le lendemain.
        LocalDate day = LocalDate.of(2026, 9, 27);
        UUID player = UUID.randomUUID();
        when(challengeRepository.findUsersCompletingAllDailyChallenges(
                eq(Instant.parse("2026-09-26T22:00:00Z")), eq(Instant.parse("2026-09-27T22:00:00Z")),
                eq(KEY), eq(SUMMARY), eq(null), any(Pageable.class)))
                .thenReturn(List.of(player));

        var page = typeAt(Instant.parse("2026-09-27T10:00:00Z"))
                .findCandidates(new AllDailyCompletedParameters(day, day), new AutoChallengeCandidateQuery(SUMMARY, null, 10));

        assertThat(page.userIds()).containsExactly(player);
        assertThat(page.nextCursor()).isNull();
    }

    @Test void progressComparesDoneWithDueDailies() {
        when(challengeRepository.countDailyChallengesDue(PLAYER, START_INCLUSIVE, END_EXCLUSIVE, KEY)).thenReturn(3L);
        when(challengeRepository.countDailyChallengesCompleted(PLAYER, START_INCLUSIVE, END_EXCLUSIVE, KEY)).thenReturn(2L);

        var progress = typeAt(LAST_DAY).progress(parameters(), PLAYER);

        assertThat(progress.current()).isEqualTo(2);
        assertThat(progress.target()).isEqualTo(3);
        assertThat(progress.completed()).isFalse();
    }

    @Test void progressCompletesWhenEveryDueDailyIsDone() {
        when(challengeRepository.countDailyChallengesDue(PLAYER, START_INCLUSIVE, END_EXCLUSIVE, KEY)).thenReturn(3L);
        when(challengeRepository.countDailyChallengesCompleted(PLAYER, START_INCLUSIVE, END_EXCLUSIVE, KEY)).thenReturn(3L);

        assertThat(typeAt(LAST_DAY).progress(parameters(), PLAYER).completed()).isTrue();
    }

    @Test void aPeriodWithoutDueDailiesHasNoProgress() {
        when(challengeRepository.countDailyChallengesDue(PLAYER, START_INCLUSIVE, END_EXCLUSIVE, KEY)).thenReturn(0L);

        assertThat(typeAt(LAST_DAY).progress(parameters(), PLAYER)).isNull();
        verify(challengeRepository, never()).countDailyChallengesCompleted(any(), any(), any(), any());
    }

    @Test void reactsToEveryValidation() {
        // Sans ce déclencheur, rien ne tombe avant le balayage horaire.
        assertThat(typeAt(LAST_DAY).triggers()).contains(AutoChallengeTrigger.CHALLENGE_VALIDATED);
    }

    @Test void exposesRequiredStartAndEndDateParameters() {
        var type = typeAt(LAST_DAY);
        assertThat(type.key()).isEqualTo(KEY);
        assertThat(type.parameters()).extracting(parameter -> parameter.name())
                .containsExactly("startDate", "endDate");
        assertThat(type.parameters()).allSatisfy(parameter -> {
            assertThat(parameter.required()).isTrue();
            assertThat(parameter.type().name()).isEqualTo("DATE");
        });
    }

    @Test void rejectsReversedDateWindow() {
        assertThatThrownBy(() -> typeAt(LAST_DAY).validate(new AllDailyCompletedParameters(END_DATE, START_DATE)))
                .hasMessage("startDate must be on or before endDate.");
    }

    private static AllDailyCompletedParameters parameters() {
        return new AllDailyCompletedParameters(START_DATE, END_DATE);
    }
}
