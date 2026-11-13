package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.place.domain.PlaceResetOutbox;
import fr.insastronaute.webapi.place.repository.PlaceResetOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PlaceResetOutboxPublisherTest {
    @Test
    void brokerFailureKeepsEntryForRetryThenSuccessDeletesIt() {
        PlaceResetOutboxRepository repository = mock(PlaceResetOutboxRepository.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        PlaceResetOutbox entry = new PlaceResetOutbox(81);
        when(repository.findByRevision("81")).thenReturn(Optional.of(entry));
        doThrow(new IllegalStateException("broker unavailable")).when(messaging)
                .convertAndSend("/topic/canvas-updates", new PlaceCanvasResetEvent("CANVAS_RESET", 81));
        PlaceResetOutboxPublisher publisher = new PlaceResetOutboxPublisher(repository, messaging);

        assertThat(publisher.tryPublish(81)).isFalse();
        assertThat(entry.getAttempts()).isEqualTo(1);
        verify(repository).save(entry);
        verify(repository, never()).delete(entry);

        reset(messaging);
        when(repository.findTop20ByOrderByCreatedAtAsc()).thenReturn(List.of(entry));
        publisher.retryPending();

        verify(messaging).convertAndSend(
                "/topic/canvas-updates", new PlaceCanvasResetEvent("CANVAS_RESET", 81));
        verify(repository).delete(entry);
    }
}
