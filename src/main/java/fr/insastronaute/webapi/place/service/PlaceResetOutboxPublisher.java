package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.place.domain.PlaceResetOutbox;
import fr.insastronaute.webapi.place.repository.PlaceResetOutboxRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlaceResetOutboxPublisher {
    private final PlaceResetOutboxRepository repository;
    private final SimpMessagingTemplate messaging;

    public PlaceResetOutboxPublisher(PlaceResetOutboxRepository repository, SimpMessagingTemplate messaging) {
        this.repository = repository;
        this.messaging = messaging;
    }

    public void record(long revision) {
        repository.save(new PlaceResetOutbox(revision));
    }

    public boolean tryPublish(long revision) {
        return repository.findByRevision(Long.toString(revision))
                .map(this::tryPublish)
                .orElse(true);
    }

    @Scheduled(fixedDelayString = "${app.place.reset-outbox-retry-ms:5000}")
    public void retryPending() {
        repository.findTop20ByOrderByCreatedAtAsc().forEach(this::tryPublish);
    }

    @Transactional
    protected boolean tryPublish(PlaceResetOutbox entry) {
        try {
            messaging.convertAndSend("/topic/canvas-updates",
                    new PlaceCanvasResetEvent("CANVAS_RESET", entry.getRevision()));
            repository.delete(entry);
            return true;
        } catch (RuntimeException | Error failure) {
            entry.incrementAttempts();
            repository.save(entry);
            return false;
        }
    }
}
