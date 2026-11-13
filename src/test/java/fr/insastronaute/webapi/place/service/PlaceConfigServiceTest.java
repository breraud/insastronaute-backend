package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.place.domain.PlaceConfig;
import fr.insastronaute.webapi.place.domain.PlaceResizeMode;
import fr.insastronaute.webapi.place.dto.PlaceConfigUpdateDto;
import fr.insastronaute.webapi.place.dto.PlaceConfigDto;
import fr.insastronaute.webapi.place.repository.PlaceConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlaceConfigServiceTest {

    @Test
    void resizeModeIsRequiredOnlyWhenDimensionsChange() throws Exception {
        PlaceConfigRepository repository = mock(PlaceConfigRepository.class);
        PlaceConfig config = existingConfig();
        PlaceCanvasResizer resizer = mock(PlaceCanvasResizer.class);
        PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        PlaceConfigService service = service(repository, resizer, canvasStore, messaging);
        when(repository.findById(PlaceConfig.SINGLETON_ID)).thenReturn(Optional.of(config));
        when(repository.saveAndFlush(config)).thenReturn(config);

        PlaceConfigUpdateDto unchanged = update(500, 500);
        assertThat(service.updateConfig(unchanged).canvasWidth()).isEqualTo(500);

        Throwable failure = catchThrowable(() -> service.updateConfig(update(600, 500)));

        assertThat(failure).isNotNull();
        Method getCode = failure.getClass().getMethod("getCode");
        assertThat(getCode.invoke(failure)).isEqualTo("PLACE_RESIZE_MODE_REQUIRED");
        verify(canvasStore, never()).readCanvas();
        verifyNoInteractions(resizer, messaging);
    }

    @Test
    void dimensionChangeResizesAndActivatesCanvasBeforeSavingAndBroadcastsNewRevision() {
        PlaceConfigRepository repository = mock(PlaceConfigRepository.class);
        PlaceCanvasResizer resizer = mock(PlaceCanvasResizer.class);
        PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        PlaceConfig config = existingConfig();
        byte[] source = new byte[500 * 500 * 3];
        byte[] resized = new byte[600 * 400 * 3];
        PlaceCanvasDescriptor original = new PlaceCanvasDescriptor("old", 41, 500, 500);
        when(repository.findById(PlaceConfig.SINGLETON_ID)).thenReturn(Optional.of(config));
        when(canvasStore.readCanvas()).thenReturn(new PlaceCanvasStore.CanvasRead(source, original));
        when(resizer.resize(source, 500, 500, 600, 400, PlaceResizeMode.PRESERVE_CENTERED)).thenReturn(resized);
        when(canvasStore.activateResizedGeneration(41, 600, 400, resized)).thenReturn(true);
        when(repository.saveAndFlush(config)).thenReturn(config);

        var result = service(repository, resizer, canvasStore, messaging)
                .updateConfig(update(600, 400, PlaceResizeMode.PRESERVE_CENTERED));

        assertThat(result.canvasWidth()).isEqualTo(600);
        var order = org.mockito.Mockito.inOrder(canvasStore, resizer, repository, messaging);
        order.verify(canvasStore).readCanvas();
        order.verify(resizer).resize(source, 500, 500, 600, 400, PlaceResizeMode.PRESERVE_CENTERED);
        order.verify(canvasStore).activateResizedGeneration(41, 600, 400, resized);
        order.verify(repository).saveAndFlush(config);
        order.verify(messaging).convertAndSend("/topic/canvas-updates", new PlaceCanvasResetEvent("CANVAS_RESET", 42));
    }

    @Test
    void activationFailureDoesNotSaveOrBroadcast() {
        PlaceConfigRepository repository = mock(PlaceConfigRepository.class);
        PlaceCanvasResizer resizer = mock(PlaceCanvasResizer.class);
        PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        PlaceConfig config = existingConfig();
        byte[] source = new byte[500 * 500 * 3];
        byte[] reset = new byte[600 * 400 * 3];
        when(repository.findById(PlaceConfig.SINGLETON_ID)).thenReturn(Optional.of(config));
        when(canvasStore.readCanvas()).thenReturn(new PlaceCanvasStore.CanvasRead(
                source, new PlaceCanvasDescriptor("old", 8, 500, 500)));
        when(resizer.resize(source, 500, 500, 600, 400, PlaceResizeMode.RESET)).thenReturn(reset);
        when(canvasStore.activateResizedGeneration(8, 600, 400, reset)).thenReturn(false);

        Throwable failure = catchThrowable(() -> service(repository, resizer, canvasStore, messaging)
                .updateConfig(update(600, 400, PlaceResizeMode.RESET)));

        assertThat(failure).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).saveAndFlush(config);
        verify(messaging, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void persistenceFailureRestoresPreviousGenerationAndDoesNotBroadcast() {
        PlaceConfigRepository repository = mock(PlaceConfigRepository.class);
        PlaceCanvasResizer resizer = mock(PlaceCanvasResizer.class);
        PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        PlaceConfig config = existingConfig();
        byte[] source = new byte[500 * 500 * 3];
        byte[] resized = new byte[600 * 400 * 3];
        when(repository.findById(PlaceConfig.SINGLETON_ID)).thenReturn(Optional.of(config));
        when(canvasStore.readCanvas()).thenReturn(new PlaceCanvasStore.CanvasRead(
                source, new PlaceCanvasDescriptor("old", 14, 500, 500)));
        when(resizer.resize(source, 500, 500, 600, 400, PlaceResizeMode.PRESERVE_CENTERED)).thenReturn(resized);
        when(canvasStore.activateResizedGeneration(14, 600, 400, resized)).thenReturn(true);
        when(repository.saveAndFlush(config)).thenThrow(new RuntimeException("database unavailable"));
        when(canvasStore.activateExistingGeneration(15, "old", 500, 500)).thenReturn(true);

        Throwable failure = catchThrowable(() -> service(repository, resizer, canvasStore, messaging)
                .updateConfig(update(600, 400, PlaceResizeMode.PRESERVE_CENTERED)));

        assertThat(failure).hasMessage("database unavailable");
        verify(canvasStore).activateExistingGeneration(15, "old", 500, 500);
        verify(messaging, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void commitRollbackRestoresPreviousGenerationAndDoesNotBroadcast() {
        PlaceConfigRepository repository = mock(PlaceConfigRepository.class);
        PlaceCanvasResizer resizer = mock(PlaceCanvasResizer.class);
        PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        PlatformTransactionManager transactions = transactionManager(
                new org.springframework.transaction.UnexpectedRollbackException("commit rolled back"));
        PlaceConfig config = existingConfig();
        byte[] source = new byte[500 * 500 * 3];
        byte[] resized = new byte[600 * 400 * 3];
        when(repository.findById(PlaceConfig.SINGLETON_ID)).thenReturn(Optional.of(config));
        when(canvasStore.readCanvas()).thenReturn(new PlaceCanvasStore.CanvasRead(
                source, new PlaceCanvasDescriptor("old", 30, 500, 500)));
        when(resizer.resize(source, 500, 500, 600, 400, PlaceResizeMode.RESET)).thenReturn(resized);
        when(canvasStore.activateResizedGeneration(30, 600, 400, resized)).thenReturn(true);
        when(repository.saveAndFlush(config)).thenReturn(config);
        when(canvasStore.activateExistingGeneration(31, "old", 500, 500)).thenReturn(true);

        Throwable failure = catchThrowable(() -> service(repository, resizer, canvasStore, messaging, transactions)
                .updateConfig(update(600, 400, PlaceResizeMode.RESET)));

        assertThat(failure).isInstanceOf(org.springframework.transaction.UnexpectedRollbackException.class);
        verify(canvasStore).activateExistingGeneration(31, "old", 500, 500);
        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void failedCompensationIsSuppressedOnPrimaryCommitFailure() {
        PlaceConfigRepository repository = mock(PlaceConfigRepository.class);
        PlaceCanvasResizer resizer = mock(PlaceCanvasResizer.class);
        PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        PlatformTransactionManager transactions = transactionManager(
                new org.springframework.transaction.UnexpectedRollbackException("commit rolled back"));
        PlaceConfig config = existingConfig();
        byte[] source = new byte[500 * 500 * 3];
        byte[] resized = new byte[600 * 400 * 3];
        when(repository.findById(PlaceConfig.SINGLETON_ID)).thenReturn(Optional.of(config));
        when(canvasStore.readCanvas()).thenReturn(new PlaceCanvasStore.CanvasRead(
                source, new PlaceCanvasDescriptor("old", 50, 500, 500)));
        when(resizer.resize(source, 500, 500, 600, 400, PlaceResizeMode.RESET)).thenReturn(resized);
        when(canvasStore.activateResizedGeneration(50, 600, 400, resized)).thenReturn(true);
        when(repository.saveAndFlush(config)).thenReturn(config);
        when(canvasStore.activateExistingGeneration(51, "old", 500, 500)).thenReturn(false);

        Throwable failure = catchThrowable(() -> service(repository, resizer, canvasStore, messaging, transactions)
                .updateConfig(update(600, 400, PlaceResizeMode.RESET)));

        assertThat(failure).hasMessage("commit rolled back");
        assertThat(failure.getSuppressed()).singleElement()
                .extracting(Throwable::getMessage)
                .isEqualTo("Impossible de restaurer le canevas précédent après l'échec de configuration.");
    }

    @Test
    void throwingCompensationIsSuppressedOnPrimaryDatabaseFailure() {
        PlaceConfigRepository repository = mock(PlaceConfigRepository.class);
        PlaceCanvasResizer resizer = mock(PlaceCanvasResizer.class);
        PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        PlaceConfig config = existingConfig();
        byte[] source = new byte[500 * 500 * 3];
        byte[] resized = new byte[600 * 400 * 3];
        when(repository.findById(PlaceConfig.SINGLETON_ID)).thenReturn(Optional.of(config));
        when(canvasStore.readCanvas()).thenReturn(new PlaceCanvasStore.CanvasRead(
                source, new PlaceCanvasDescriptor("old", 70, 500, 500)));
        when(resizer.resize(source, 500, 500, 600, 400, PlaceResizeMode.RESET)).thenReturn(resized);
        when(canvasStore.activateResizedGeneration(70, 600, 400, resized)).thenReturn(true);
        when(repository.saveAndFlush(config)).thenThrow(new RuntimeException("database unavailable"));
        when(canvasStore.activateExistingGeneration(71, "old", 500, 500))
                .thenThrow(new IllegalStateException("redis unavailable"));

        Throwable failure = catchThrowable(() -> service(repository, resizer, canvasStore, messaging)
                .updateConfig(update(600, 400, PlaceResizeMode.RESET)));

        assertThat(failure).hasMessage("database unavailable");
        assertThat(failure.getSuppressed()).singleElement()
                .extracting(Throwable::getMessage).isEqualTo("redis unavailable");
        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void broadcastFailureAfterCommitLeavesDurableResetForRetryAndReturnsCommittedConfig() {
        PlaceConfigRepository repository = mock(PlaceConfigRepository.class);
        PlaceCanvasResizer resizer = mock(PlaceCanvasResizer.class);
        PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        PlaceConfig config = existingConfig();
        byte[] source = new byte[500 * 500 * 3];
        byte[] resized = new byte[600 * 400 * 3];
        when(repository.findById(PlaceConfig.SINGLETON_ID)).thenReturn(Optional.of(config));
        when(canvasStore.readCanvas()).thenReturn(new PlaceCanvasStore.CanvasRead(
                source, new PlaceCanvasDescriptor("old", 80, 500, 500)));
        when(resizer.resize(source, 500, 500, 600, 400, PlaceResizeMode.RESET)).thenReturn(resized);
        when(canvasStore.activateResizedGeneration(80, 600, 400, resized)).thenReturn(true);
        when(repository.saveAndFlush(config)).thenReturn(config);
        PlaceResetOutboxPublisher publisher = mock(PlaceResetOutboxPublisher.class);
        when(publisher.tryPublish(81)).thenReturn(false);

        PlaceConfigDto result = service(repository, resizer, canvasStore, messaging,
                transactionManager(), publisher).updateConfig(update(600, 400, PlaceResizeMode.RESET));

        assertThat(result.canvasWidth()).isEqualTo(600);
        verify(publisher).record(81);
        verify(publisher).tryPublish(81);
        verify(canvasStore, never()).activateExistingGeneration(anyLong(), anyString(), anyInt(), anyInt());
        verify(repository).saveAndFlush(config);
    }

    @Test
    void cleanupFailureAfterCommitStillPublishesResetAndReturnsCommittedConfig() {
        PlaceConfigRepository repository = mock(PlaceConfigRepository.class);
        PlaceCanvasResizer resizer = mock(PlaceCanvasResizer.class);
        PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        PlaceConfig config = existingConfig();
        byte[] source = new byte[500 * 500 * 3];
        byte[] resized = new byte[600 * 400 * 3];
        when(repository.findById(PlaceConfig.SINGLETON_ID)).thenReturn(Optional.of(config));
        when(canvasStore.readCanvas()).thenReturn(new PlaceCanvasStore.CanvasRead(
                source, new PlaceCanvasDescriptor("old", 90, 500, 500)));
        when(resizer.resize(source, 500, 500, 600, 400, PlaceResizeMode.RESET)).thenReturn(resized);
        when(canvasStore.activateResizedGeneration(90, 600, 400, resized)).thenReturn(true);
        when(repository.saveAndFlush(config)).thenReturn(config);
        doThrow(new IllegalStateException("redis cleanup unavailable"))
                .when(canvasStore).deleteGeneration("old");

        PlaceConfigDto result = service(repository, resizer, canvasStore, messaging)
                .updateConfig(update(600, 400, PlaceResizeMode.RESET));

        assertThat(result.canvasWidth()).isEqualTo(600);
        var order = org.mockito.Mockito.inOrder(messaging, canvasStore);
        order.verify(messaging).convertAndSend(
                "/topic/canvas-updates", new PlaceCanvasResetEvent("CANVAS_RESET", 91));
        order.verify(canvasStore).deleteGeneration("old");
    }

    @Test
    void leaseLossAfterActivationBeforeCommitRollsBackAndCompensates() {
        PlaceConfigRepository repository = mock(PlaceConfigRepository.class);
        PlaceCanvasResizer resizer = mock(PlaceCanvasResizer.class);
        PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        PlaceConfig config = existingConfig();
        byte[] source = new byte[500 * 500 * 3];
        byte[] resized = new byte[600 * 400 * 3];
        when(repository.findById(PlaceConfig.SINGLETON_ID)).thenReturn(Optional.of(config));
        when(canvasStore.readCanvas()).thenReturn(new PlaceCanvasStore.CanvasRead(
                source, new PlaceCanvasDescriptor("old", 100, 500, 500)));
        when(resizer.resize(source, 500, 500, 600, 400, PlaceResizeMode.RESET)).thenReturn(resized);
        when(canvasStore.activateResizedGeneration(100, 600, 400, resized)).thenReturn(true);
        when(repository.saveAndFlush(config)).thenReturn(config);
        when(canvasStore.activateExistingGeneration(101, "old", 500, 500)).thenReturn(true);
        PlaceConfigService service = service(repository, resizer, canvasStore, messaging);
        doThrow(new PlaceResizeLockedException()).when(canvasStore)
                .assertResizeLeaseOwned(any(PlaceCanvasStore.ResizeLease.class));

        Throwable failure = catchThrowable(() ->
                service.updateConfig(update(600, 400, PlaceResizeMode.RESET)));

        assertThat(failure).isInstanceOf(PlaceResizeLockedException.class);
        verify(canvasStore).activateExistingGeneration(101, "old", 500, 500);
        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    private static PlaceConfig existingConfig() {
        PlaceConfig config = new PlaceConfig();
        config.setActive(false);
        config.setStartDate(Instant.parse("2026-07-22T10:00:00Z"));
        config.setEndDate(Instant.parse("2026-07-23T10:00:00Z"));
        config.setCanvasWidth(500);
        config.setCanvasHeight(500);
        config.setBaseCooldownMs(28_800_000);
        config.setChallengeCooldownMs(300_000);
        config.setChallengeRatio(50);
        return config;
    }

    private static PlaceConfigUpdateDto update(int canvasWidth, int canvasHeight) {
        return update(canvasWidth, canvasHeight, null);
    }

    private static PlaceConfigUpdateDto update(int canvasWidth, int canvasHeight, PlaceResizeMode mode) {
        return new PlaceConfigUpdateDto(
                false,
                Instant.parse("2026-07-22T10:00:00Z"),
                Instant.parse("2026-07-23T10:00:00Z"),
                canvasWidth,
                canvasHeight,
                28_800_000,
                300_000,
                50,
                30L,
                1L,
                null,
                null,
                null,
                null,
                mode
        );
    }

    private static PlaceConfigService service(PlaceConfigRepository repository, PlaceCanvasResizer resizer,
                                              PlaceCanvasStore canvasStore, SimpMessagingTemplate messaging) {
        return service(repository, resizer, canvasStore, messaging, transactionManager());
    }

    private static PlaceConfigService service(PlaceConfigRepository repository, PlaceCanvasResizer resizer,
                                              PlaceCanvasStore canvasStore, SimpMessagingTemplate messaging,
                                              PlatformTransactionManager transactions) {
        PlaceCanvasStore.ResizeLease lease = new PlaceCanvasStore.ResizeLease("test-owner");
        when(canvasStore.acquireResizeLease(anyInt(), anyInt())).thenReturn(lease);
        when(canvasStore.activateResizedGeneration(eq(lease), anyLong(), anyInt(), anyInt(), any(byte[].class)))
                .thenAnswer(call -> {
                    long revision = call.getArgument(1);
                    boolean activated = canvasStore.activateResizedGeneration(
                            revision, call.getArgument(2), call.getArgument(3), call.getArgument(4));
                    return activated ? new PlaceCanvasStore.ResizeActivation("new", revision + 1) : null;
                });
        when(canvasStore.activateExistingGeneration(eq(lease), anyLong(), anyString(), anyInt(), anyInt()))
                .thenAnswer(call -> canvasStore.activateExistingGeneration(
                        call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4)));
        return new PlaceConfigService(repository, resizer, canvasStore, messaging, transactions);
    }

    private static PlaceConfigService service(PlaceConfigRepository repository, PlaceCanvasResizer resizer,
                                              PlaceCanvasStore canvasStore, SimpMessagingTemplate messaging,
                                              PlatformTransactionManager transactions,
                                              PlaceResetOutboxPublisher publisher) {
        PlaceCanvasStore.ResizeLease lease = new PlaceCanvasStore.ResizeLease("test-owner");
        when(canvasStore.acquireResizeLease(anyInt(), anyInt())).thenReturn(lease);
        when(canvasStore.activateResizedGeneration(eq(lease), anyLong(), anyInt(), anyInt(), any(byte[].class)))
                .thenAnswer(call -> {
                    long revision = call.getArgument(1);
                    boolean activated = canvasStore.activateResizedGeneration(
                            revision, call.getArgument(2), call.getArgument(3), call.getArgument(4));
                    return activated ? new PlaceCanvasStore.ResizeActivation("new", revision + 1) : null;
                });
        when(canvasStore.activateExistingGeneration(eq(lease), anyLong(), anyString(), anyInt(), anyInt()))
                .thenAnswer(call -> canvasStore.activateExistingGeneration(
                        call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4)));
        return new PlaceConfigService(repository, resizer, canvasStore, messaging, transactions, publisher);
    }

    private static PlatformTransactionManager transactionManager() {
        return transactionManager(null);
    }

    private static PlatformTransactionManager transactionManager(RuntimeException commitFailure) {
        return new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) {
                if (commitFailure != null) throw commitFailure;
            }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        };
    }
}
