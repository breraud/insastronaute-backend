package fr.insastronaute.webapi.sts.multiplayer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatBroadcaster;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpMode;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpQueueStatusResponse;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpChallengeRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpDeckRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpNotificationRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpQueueEntryRepository;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.sts.service.StsGameDataService;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le compte de joueurs en recherche, celui que le menu multijoueur affiche.
 *
 * <p>Ce qui est vérifié ici n'est pas l'arithmétique — c'est un {@code count} — mais les deux
 * choses qui décident de ce qu'il vaut : la file interrogée est bien celle du mode demandé, et
 * la fenêtre de fraîcheur exclut les entrées qui ne battent plus.
 */
class StsPvpQueueStatusTest {

    private final StsPvpQueueEntryRepository queueRepository = mock(StsPvpQueueEntryRepository.class);

    private final StsPvpService service = new StsPvpService(
            mock(StsPvpProfileRepository.class),
            mock(StsPvpDeckRepository.class),
            queueRepository,
            mock(StsPvpChallengeRepository.class),
            mock(StsPvpBattleRepository.class),
            mock(StsPvpNotificationRepository.class),
            mock(UserRepository.class),
            mock(fr.insastronaute.webapi.friend.repository.FriendshipRepository.class),
            mock(DepartmentRepository.class),
            mock(CardService.class),
            mock(StsGameDataService.class),
            mock(StsRunRepository.class),
            new ObjectMapper(),
            mock(StsPvpCombatService.class),
            mock(StsPvpCombatBroadcaster.class),
            mock(fr.insastronaute.webapi.card.service.CollectionTestModeService.class));

    @Test
    void itCountsTheQueueOfTheModeItWasAskedFor() {
        when(queueRepository.countByModeAndFriendlyAndUpdatedAtAfter(any(), anyBoolean(), any()))
                .thenReturn(3L);

        StsPvpQueueStatusResponse status = service.queueStatus("TWO_V_TWO", false);

        assertThat(status.mode()).isEqualTo("TWO_V_TWO");
        assertThat(status.waiting()).isEqualTo(3L);
        // Le menu peut donc dire « 3/4 » sans connaître les règles des modes.
        assertThat(status.required()).isEqualTo(4);

        ArgumentCaptor<StsPvpMode> mode = ArgumentCaptor.forClass(StsPvpMode.class);
        verify(queueRepository)
                .countByModeAndFriendlyAndUpdatedAtAfter(mode.capture(), any(), any());
        assertThat(mode.getValue()).isEqualTo(StsPvpMode.TWO_V_TWO);
    }

    /**
     * La fenêtre est celle qui décide ailleurs qu'une entrée est encore valable. Annoncer des
     * joueurs que l'appariement ne prendrait plus serait pire que ne rien annoncer : le menu
     * promettrait un adversaire que la recherche ne trouverait jamais.
     */
    @Test
    void itIgnoresEntriesThatHaveStoppedBeating() {
        when(queueRepository.countByModeAndFriendlyAndUpdatedAtAfter(any(), anyBoolean(), any()))
                .thenReturn(0L);

        Instant before = Instant.now();
        service.queueStatus(null, true);
        Instant after = Instant.now();

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Boolean> friendly = ArgumentCaptor.forClass(Boolean.class);
        verify(queueRepository)
                .countByModeAndFriendlyAndUpdatedAtAfter(any(), friendly.capture(), since.capture());

        assertThat(friendly.getValue()).isTrue();
        assertThat(since.getValue())
                .isBetween(before.minusSeconds(31), after.minusSeconds(29));
    }

    /** Un mode absent vaut le duel, comme partout ailleurs ; un mode inventé est une erreur. */
    @Test
    void anAbsentModeMeansTheDuelAndAnUnknownOneIsRefused() {
        when(queueRepository.countByModeAndFriendlyAndUpdatedAtAfter(any(), anyBoolean(), any()))
                .thenReturn(1L);

        assertThat(service.queueStatus(null, false).mode()).isEqualTo("ONE_V_ONE");
        assertThat(service.queueStatus(null, false).required()).isEqualTo(2);

        assertThatThrownBy(() -> service.queueStatus("3v3", false))
                .isInstanceOf(BadRequestException.class);
    }
}
