package fr.insastronaute.webapi.site.service;

import fr.insastronaute.webapi.site.domain.SiteAvailability;
import fr.insastronaute.webapi.site.dto.SiteAvailabilityUpdateRequest;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiteAvailabilityServiceTest {

    private SiteAvailabilityStore store;
    private AtomicLong clock;
    private SiteAvailabilityService service;

    private static SiteAvailability row(boolean enabled, String message) {
        SiteAvailability entity = new SiteAvailability();
        entity.setPlayerAccessEnabled(enabled);
        entity.setMessage(message);
        return entity;
    }

    @BeforeEach
    void setUp() {
        store = mock(SiteAvailabilityStore.class);
        clock = new AtomicLong(0L);
        service = new SiteAvailabilityService(store, clock::get);
    }

    @Test
    void secondReadInsideTheTtlDoesNotTouchTheDatabase() {
        when(store.getOrCreate()).thenReturn(row(true, null));

        service.currentStatus();
        service.currentStatus();

        verify(store, times(1)).getOrCreate();
    }

    @Test
    void readAfterTheTtlReloads() {
        when(store.getOrCreate()).thenReturn(row(true, null));
        service.currentStatus();

        clock.addAndGet(6_000_000_000L);
        service.currentStatus();

        verify(store, times(2)).getOrCreate();
    }

    /**
     * Les deux cas ci-dessus encadrent la borne a une seconde de distance sans
     * jamais la toucher : la comparaison pourrait passer de {@code <} a {@code <=},
     * ou la constante gagner une seconde, sans qu'aucun test ne bronche. Ces deux
     * cas-ci se posent de part et d'autre de la nanoseconde qui separe les deux
     * comportements.
     */
    @Test
    void theLastNanosecondBeforeTheTtlStillReadsTheCache() {
        when(store.getOrCreate()).thenReturn(row(true, null));
        service.currentStatus();

        clock.addAndGet(SiteAvailabilityService.CACHE_TTL_NANOS - 1);
        service.currentStatus();

        verify(store, times(1)).getOrCreate();
    }

    @Test
    void theTtlItselfAlreadyCountsAsExpired() {
        when(store.getOrCreate()).thenReturn(row(true, null));
        service.currentStatus();

        clock.addAndGet(SiteAvailabilityService.CACHE_TTL_NANOS);
        service.currentStatus();

        verify(store, times(2)).getOrCreate();
    }

    @Test
    void aFailingDatabaseKeepsTheSiteOpen() {
        when(store.getOrCreate()).thenThrow(new IllegalStateException("database down"));

        assertThat(service.currentStatus().playerAccessEnabled()).isTrue();
    }

    @Test
    void aFailingDatabaseKeepsTheLastKnownValue() {
        when(store.getOrCreate()).thenReturn(row(false, "Retour a 18h."));
        service.currentStatus();
        clock.addAndGet(6_000_000_000L);
        when(store.getOrCreate()).thenThrow(new IllegalStateException("database down"));

        SiteAvailabilityService.SiteStatus status = service.currentStatus();

        assertThat(status.playerAccessEnabled()).isFalse();
        assertThat(status.message()).isEqualTo("Retour a 18h.");
    }

    @Test
    void blankMessageNormalisesToNullOnWrite() {
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        when(store.save(eq(true), messageCaptor.capture(), any())).thenReturn(row(true, null));

        service.update(new SiteAvailabilityUpdateRequest(true, "   "), new AppUser());

        assertThat(messageCaptor.getValue()).isNull();
    }

    @Test
    void emptyMessageNormalisesToNullOnWrite() {
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        when(store.save(eq(true), messageCaptor.capture(), any())).thenReturn(row(true, null));

        service.update(new SiteAvailabilityUpdateRequest(true, ""), new AppUser());

        assertThat(messageCaptor.getValue()).isNull();
    }

    @Test
    void nonBlankMessageIsTrimmedOnWrite() {
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        when(store.save(eq(false), messageCaptor.capture(), any())).thenReturn(row(false, "Retour a 18h."));

        service.update(new SiteAvailabilityUpdateRequest(false, "  Retour a 18h.  "), new AppUser());

        assertThat(messageCaptor.getValue()).isEqualTo("Retour a 18h.");
    }

    @Test
    void updateRefreshesTheCacheSoTheNextReadSkipsTheDatabase() {
        when(store.getOrCreate()).thenReturn(row(true, null));
        service.currentStatus();

        when(store.save(eq(false), eq("Retour a 18h."), any())).thenReturn(row(false, "Retour a 18h."));
        service.update(new SiteAvailabilityUpdateRequest(false, "Retour a 18h."), new AppUser());

        SiteAvailabilityService.SiteStatus status = service.currentStatus();

        assertThat(status.playerAccessEnabled()).isFalse();
        assertThat(status.message()).isEqualTo("Retour a 18h.");
        verify(store, times(1)).getOrCreate();
    }
}
