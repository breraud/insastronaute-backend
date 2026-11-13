package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.place.domain.PlaceConfig;
import fr.insastronaute.webapi.place.dto.PlaceConfigDto;
import fr.insastronaute.webapi.place.dto.PlaceConfigUpdateDto;
import fr.insastronaute.webapi.place.repository.PlaceConfigRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PlaceConfigService {

    private final PlaceConfigRepository repository;
    private final PlaceCanvasResizer canvasResizer;
    private final PlaceCanvasStore canvasStore;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionTemplate transactions;
    private final PlaceResetOutboxPublisher resetPublisher;

    @Autowired
    public PlaceConfigService(PlaceConfigRepository repository, PlaceCanvasResizer canvasResizer,
                              PlaceCanvasStore canvasStore, SimpMessagingTemplate messagingTemplate,
                              PlatformTransactionManager transactionManager,
                              PlaceResetOutboxPublisher resetPublisher) {
        this.repository = repository;
        this.canvasResizer = canvasResizer;
        this.canvasStore = canvasStore;
        this.messagingTemplate = messagingTemplate;
        this.transactions = new TransactionTemplate(transactionManager);
        this.resetPublisher = resetPublisher;
    }

    PlaceConfigService(PlaceConfigRepository repository, PlaceCanvasResizer canvasResizer,
                       PlaceCanvasStore canvasStore, SimpMessagingTemplate messagingTemplate,
                       PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.canvasResizer = canvasResizer;
        this.canvasStore = canvasStore;
        this.messagingTemplate = messagingTemplate;
        this.transactions = new TransactionTemplate(transactionManager);
        this.resetPublisher = null;
    }

    @Transactional
    public PlaceConfigDto getConfig() {
        return PlaceConfigDto.from(currentConfig());
    }

    @Transactional
    public PlaceConfig currentConfig() {
        return getOrCreate();
    }

    public PlaceConfigDto updateConfig(PlaceConfigUpdateDto update) {
        AtomicReference<ResizeCompensation> compensation = new AtomicReference<>();
        AtomicReference<PlaceCanvasStore.ResizeLease> leaseRef = new AtomicReference<>();
        UpdateResult result;
        try {
            result = transactions.execute(status -> {
                PlaceConfig config = getOrCreate();
                boolean dimensionsChanged = config.getCanvasWidth() != update.canvasWidth()
                        || config.getCanvasHeight() != update.canvasHeight();
                if (dimensionsChanged && update.resizeMode() == null) {
                    throw new PlaceConfigException(
                            "PLACE_RESIZE_MODE_REQUIRED",
                            "Le mode de redimensionnement est requis lorsque les dimensions changent."
                    );
                }
                long resizedRevision = 0;
                if (dimensionsChanged) {
                    PlaceCanvasStore.ResizeLease lease = canvasStore.acquireResizeLease(
                            config.getCanvasWidth(), config.getCanvasHeight());
                    if (lease == null) throw new PlaceResizeLockedException();
                    leaseRef.set(lease);
                    PlaceCanvasStore.CanvasRead canvas = canvasStore.readCanvas();
                    PlaceCanvasDescriptor previous = canvas.descriptor();
                    byte[] resized = canvasResizer.resize(canvas.rgb(), previous.width(), previous.height(),
                            update.canvasWidth(), update.canvasHeight(), update.resizeMode());
                    PlaceCanvasStore.ResizeActivation activation = canvasStore.activateResizedGeneration(
                            lease, previous.revision(), update.canvasWidth(), update.canvasHeight(), resized);
                    if (activation == null) {
                        throw new IllegalStateException("Le canevas a été modifié pendant son redimensionnement.");
                    }
                    resizedRevision = activation.revision();
                    compensation.set(new ResizeCompensation(previous, activation.generation(), resizedRevision));
                    registerBeforeCommitLeaseCheck(lease);
                    if (resetPublisher != null) resetPublisher.record(resizedRevision);
                }
                apply(config, update);
                return new UpdateResult(PlaceConfigDto.from(repository.saveAndFlush(config)), resizedRevision);
            });
            if (result == null) {
                throw new IllegalStateException("La transaction de configuration n'a produit aucun résultat.");
            }
        } catch (RuntimeException | Error failure) {
            ResizeCompensation pending = compensation.get();
            if (pending != null) restorePreviousCanvas(leaseRef.get(), pending, failure);
            throw failure;
        } finally {
            PlaceCanvasStore.ResizeLease lease = leaseRef.get();
            if (lease != null) canvasStore.releaseResizeLease(lease);
        }
        if (result.resizedRevision() != 0) {
            ResizeCompensation completed = compensation.get();
            if (resetPublisher != null) resetPublisher.tryPublish(result.resizedRevision());
            else publishReset(result.resizedRevision());
            try {
                canvasStore.deleteGeneration(completed.previous().generation());
            } catch (RuntimeException | Error ignored) {
                // SQL and canvas activation are committed; stale inactive data is safe to retain.
            }
        }
        return result.config();
    }

    private void registerBeforeCommitLeaseCheck(PlaceCanvasStore.ResizeLease lease) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                canvasStore.assertResizeLeaseOwned(lease);
            }
        });
    }

    private static void apply(PlaceConfig config, PlaceConfigUpdateDto update) {
        config.setActive(update.isActive());
        config.setStartDate(update.startDate());
        config.setEndDate(update.endDate());
        config.setCanvasWidth(update.canvasWidth());
        config.setCanvasHeight(update.canvasHeight());
        config.setBaseCooldownMs(update.baseCooldownMs());
        config.setChallengeCooldownMs(update.challengeCooldownMs());
        config.setChallengeRatio(update.challengeRatio());
        if (update.bonusPixelPrice() != null) {
            config.setBonusPixelPrice(update.bonusPixelPrice());
        }
        if (update.showPixelAuthors() != null) {
            config.setShowPixelAuthors(update.showPixelAuthors());
        }
        if (update.visibilityMode() != null) {
            config.setVisibilityMode(update.visibilityMode());
        }
        if (update.departmentCanvasEnabled() != null) {
            config.setDepartmentCanvasEnabled(update.departmentCanvasEnabled());
        }
        if (update.resultsVisible() != null) {
            config.setResultsVisible(update.resultsVisible());
        }
        if (update.bonusPixelPriceIncrement() != null) {
            config.setBonusPixelPriceIncrement(update.bonusPixelPriceIncrement());
        }
    }

    private void restorePreviousCanvas(PlaceCanvasStore.ResizeLease lease,
                                       ResizeCompensation pending, Throwable failure) {
        PlaceCanvasDescriptor previous = pending.previous();
        try {
            boolean restored = canvasStore.activateExistingGeneration(lease, pending.resizedRevision(),
                    previous.generation(), previous.width(), previous.height());
            if (restored) {
                canvasStore.deleteGeneration(pending.newGeneration());
                return;
            }
            failure.addSuppressed(new IllegalStateException(
                    "Impossible de restaurer le canevas précédent après l'échec de configuration."));
        } catch (RuntimeException | Error compensationFailure) {
            failure.addSuppressed(compensationFailure);
        }
    }

    private void publishReset(long revision) {
        messagingTemplate.convertAndSend("/topic/canvas-updates", new PlaceCanvasResetEvent("CANVAS_RESET", revision));
    }

    private record ResizeCompensation(PlaceCanvasDescriptor previous, String newGeneration, long resizedRevision) {
    }

    private record UpdateResult(PlaceConfigDto config, long resizedRevision) {
    }

    private PlaceConfig getOrCreate() {
        return repository.findById(PlaceConfig.SINGLETON_ID).orElseGet(() -> {
            Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
            PlaceConfig config = new PlaceConfig();
            config.setActive(false);
            config.setStartDate(now);
            config.setEndDate(now.plus(Duration.ofDays(1)));
            config.setCanvasWidth(500);
            config.setCanvasHeight(500);
            config.setBaseCooldownMs(Duration.ofHours(8).toMillis());
            config.setChallengeCooldownMs(Duration.ofMinutes(5).toMillis());
            config.setChallengeRatio(50);
            return repository.save(config);
        });
    }
}
