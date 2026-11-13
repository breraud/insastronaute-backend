package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.event.domain.Event;
import fr.insastronaute.webapi.event.repository.EventRepository;
import fr.insastronaute.webapi.shop.domain.*;
import fr.insastronaute.webapi.shop.dto.*;
import fr.insastronaute.webapi.shop.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopReservationServiceTest {
    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

    @Mock private ShopReservationSlotRepository slotRepository;
    @Mock private ShopReservationSeriesRepository seriesRepository;
    @Mock private ShopSettingsRepository settingsRepository;
    @Mock private ShopOrderRepository orderRepository;
    @Mock private EventRepository eventRepository;

    private ShopReservationService service;

    @BeforeEach
    void setUp() {
        service = new ShopReservationService(slotRepository, seriesRepository, settingsRepository,
                orderRepository, eventRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(slotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(seriesRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(settingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void eventImportIsUniqueAndMapsCurrentEventValues() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId, "Gala", NOW.plusSeconds(3600), NOW.plusSeconds(7200), "Hall A");
        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event));
        when(slotRepository.existsByEventId(eventId)).thenReturn(false);

        ShopReservationSlotDto created = service.createEvent(new ShopEventReservationRequest(eventId, "Retrait au bar"));

        ArgumentCaptor<ShopReservationSlot> captor = ArgumentCaptor.forClass(ShopReservationSlot.class);
        verify(slotRepository).save(captor.capture());
        ShopReservationSlot slot = captor.getValue();
        assertThat(slot.getEventStartsAtSnapshot()).isEqualTo(event.getStartsAt());
        assertThat(slot.getEventEndsAtSnapshot()).isEqualTo(event.getEndsAt());
        assertThat(slot.getEventLocationSnapshot()).isEqualTo("Hall A");
        assertThat(created.startsAt()).isEqualTo(event.getStartsAt());

        event.setStartsAt(NOW.plusSeconds(10_800));
        event.setLocation("Hall B");
        ShopReservationSlotDto synchronizedDto = service.toDto(slot);
        assertThat(synchronizedDto.startsAt()).isEqualTo(event.getStartsAt());
        assertThat(synchronizedDto.location()).isEqualTo("Hall B");
    }

    @Test
    void duplicateEventImportIsRejected() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event(eventId, "Gala", NOW, NOW.plusSeconds(1), "Hall")));
        when(slotRepository.existsByEventId(eventId)).thenReturn(true);

        assertThatThrownBy(() -> service.createEvent(new ShopEventReservationRequest(eventId, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("déjà");
    }

    @Test
    void eventImportRequiresCompleteAndOrderedScheduleWithLocation() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId, "Gala", NOW.plusSeconds(3600), null, "Hall");
        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.createEvent(new ShopEventReservationRequest(eventId, null)))
                .isInstanceOf(BadRequestException.class);

        event.setEndsAt(event.getStartsAt());
        assertThatThrownBy(() -> service.createEvent(new ShopEventReservationRequest(eventId, null)))
                .isInstanceOf(BadRequestException.class);

        event.setEndsAt(event.getStartsAt().plusSeconds(3600));
        event.setLocation("  ");
        assertThatThrownBy(() -> service.createEvent(new ShopEventReservationRequest(eventId, null)))
                .isInstanceOf(BadRequestException.class);
        verify(slotRepository, never()).save(any());
    }

    @Test
    void reservationEventOptionsOnlyContainImportableEvents() {
        Event valid = event(UUID.randomUUID(), "Gala", NOW.plusSeconds(3600), NOW.plusSeconds(7200), "Hall A");
        Event missingEnd = event(UUID.randomUUID(), "Sans fin", NOW.plusSeconds(3600), null, "Hall B");
        Event missingLocation = event(UUID.randomUUID(), "Sans lieu", NOW.plusSeconds(3600), NOW.plusSeconds(7200), " ");
        when(eventRepository.findAllByOrderByStartsAtAscCreatedAtDesc())
                .thenReturn(List.of(valid, missingEnd, missingLocation));

        assertThat(service.listEventOptions())
                .containsExactly(new ShopReservationEventDto(
                        valid.getId(), "Gala", valid.getStartsAt(), valid.getEndsAt(), "Hall A"));
    }

    @Test
    void customSlotRequiresOrderedTimesAndLocation() {
        assertThatThrownBy(() -> service.createCustom(new ShopCustomReservationRequest(
                NOW.plusSeconds(3600), NOW.plusSeconds(1800), "Local", null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.createCustom(new ShopCustomReservationRequest(
                NOW.plusSeconds(3600), NOW.plusSeconds(7200), "  ", null)))
                .isInstanceOf(BadRequestException.class);

        ShopReservationSlotDto dto = service.createCustom(new ShopCustomReservationRequest(
                NOW.plusSeconds(3600), NOW.plusSeconds(7200), "  Local  ", " Note "));
        assertThat(dto.location()).isEqualTo("Local");
        assertThat(dto.note()).isEqualTo("Note");
    }

    @Test
    void recurringSeriesMaterializesEveryInclusiveWeeklyOccurrenceInParis() {
        ShopRecurringReservationRequest request = new ShopRecurringReservationRequest(
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 17), DayOfWeek.TUESDAY,
                LocalTime.of(18, 0), LocalTime.of(20, 0), "Kfet", "Bar seulement");

        List<ShopReservationSlotDto> result = service.createRecurring(request);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(ShopReservationSlotDto::startsAt).containsExactly(
                Instant.parse("2026-03-03T17:00:00Z"),
                Instant.parse("2026-03-10T17:00:00Z"),
                Instant.parse("2026-03-17T17:00:00Z"));
        ArgumentCaptor<List<ShopReservationSlot>> slots = slotListCaptor();
        verify(slotRepository).saveAll(slots.capture());
        assertThat(slots.getValue()).allSatisfy(slot -> assertThat(slot.getSeries()).isNotNull());
    }

    @Test
    void recurringSeriesValidatesDatesTimesAndLocation() {
        assertThatThrownBy(() -> service.createRecurring(new ShopRecurringReservationRequest(
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 2), DayOfWeek.TUESDAY,
                LocalTime.NOON, LocalTime.of(13, 0), "Kfet", null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.createRecurring(new ShopRecurringReservationRequest(
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 10), DayOfWeek.TUESDAY,
                LocalTime.of(13, 0), LocalTime.NOON, "Kfet", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void recurringSeriesRejectsRangeWithoutOccurrenceBeforeSaving() {
        ShopRecurringReservationRequest request = new ShopRecurringReservationRequest(
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), DayOfWeek.TUESDAY,
                LocalTime.NOON, LocalTime.of(13, 0), "Kfet", null);

        assertThatThrownBy(() -> service.createRecurring(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("occurrence");
        verify(seriesRepository, never()).save(any());
        verify(slotRepository, never()).saveAll(any());
    }

    @Test
    void recurringSeriesRejectsParisDstGapAndOverlapBeforeSaving() {
        ShopRecurringReservationRequest gap = new ShopRecurringReservationRequest(
                LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 29), DayOfWeek.SUNDAY,
                LocalTime.of(2, 30), LocalTime.of(3, 30), "Kfet", null);
        ShopRecurringReservationRequest overlap = new ShopRecurringReservationRequest(
                LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25), DayOfWeek.SUNDAY,
                LocalTime.of(2, 30), LocalTime.of(3, 30), "Kfet", null);

        assertThatThrownBy(() -> service.createRecurring(gap))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("changement d'heure");
        assertThatThrownBy(() -> service.createRecurring(overlap))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("changement d'heure");
        verify(seriesRepository, never()).save(any());
        verify(slotRepository, never()).saveAll(any());
    }

    @Test
    void recurringSeriesRejectsMoreThan366OccurrencesBeforeSaving() {
        ShopRecurringReservationRequest request = new ShopRecurringReservationRequest(
                LocalDate.of(2026, 1, 6), LocalDate.of(2033, 1, 18), DayOfWeek.TUESDAY,
                LocalTime.NOON, LocalTime.of(13, 0), "Kfet", null);

        assertThatThrownBy(() -> service.createRecurring(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("366");
        verify(seriesRepository, never()).save(any());
        verify(slotRepository, never()).saveAll(any());
    }

    @Test
    void playerListOnlyReturnsActiveFutureSlotsSortedByEffectiveStart() {
        ShopReservationSlot past = custom(NOW.minusSeconds(60), true);
        ShopReservationSlot inactive = custom(NOW.plusSeconds(60), false);
        ShopReservationSlot later = custom(NOW.plusSeconds(3600), true);
        ShopReservationSlot sooner = custom(NOW.plusSeconds(120), true);
        when(slotRepository.findAllWithEventAndSeries()).thenReturn(List.of(past, inactive, later, sooner));

        assertThat(service.listAvailable()).extracting(ShopReservationSlotDto::startsAt)
                .containsExactly(sooner.getCustomStartsAt(), later.getCustomStartsAt());
    }

    @Test
    void playerListDoesNotExposeDeactivatedEventWhoseLiveScheduleBecameInvalid() {
        Event event = event(UUID.randomUUID(), "Gala", NOW.plusSeconds(3600), null, "Hall");
        ShopReservationSlot slot = eventSlot(event);
        slot.setActive(false);
        when(slotRepository.findAllWithEventAndSeries()).thenReturn(List.of(slot));

        assertThat(service.listAvailable()).isEmpty();
    }

    @Test
    void adminListIncludesPastAndInactiveSlots() {
        ShopReservationSlot inactive = custom(NOW.minusSeconds(60), false);
        when(slotRepository.findAllWithEventAndSeries()).thenReturn(List.of(inactive));

        assertThat(service.listAdmin()).singleElement().satisfies(dto -> assertThat(dto.active()).isFalse());
    }

    @Test
    void updateChangesOnlyCustomTimingAndAllowsEventNoteChange() {
        UUID id = UUID.randomUUID();
        ShopReservationSlot custom = custom(NOW.plusSeconds(60), true);
        custom.setId(id);
        when(slotRepository.findWithEventAndSeriesById(id)).thenReturn(Optional.of(custom));

        ShopReservationSlotDto updated = service.update(id, new ShopReservationSlotUpdateRequest(
                NOW.plusSeconds(600), NOW.plusSeconds(1200), "Nouvelle salle", "nouvelle note"));
        assertThat(updated.location()).isEqualTo("Nouvelle salle");

        ShopReservationSlot eventSlot = eventSlot(event(UUID.randomUUID(), "Gala", NOW, NOW.plusSeconds(1), "Hall"));
        eventSlot.setId(id);
        when(slotRepository.findWithEventAndSeriesById(id)).thenReturn(Optional.of(eventSlot));
        ShopReservationSlotDto eventUpdated = service.update(id, new ShopReservationSlotUpdateRequest(null, null, null, "bar"));
        assertThat(eventUpdated.note()).isEqualTo("bar");
    }

    @Test
    void deactivateKeepsTheSlotButHidesItFromPlayers() {
        UUID id = UUID.randomUUID();
        ShopReservationSlot slot = custom(NOW.plusSeconds(60), true);
        when(slotRepository.findWithEventAndSeriesById(id)).thenReturn(Optional.of(slot));

        ShopReservationSlotDto result = service.deactivate(id);

        assertThat(result.active()).isFalse();
        verify(slotRepository).save(slot);
    }

    @Test
    void activateRestoredEventUnderLockAndRefreshesSnapshots() {
        UUID id = UUID.randomUUID();
        Event event = event(UUID.randomUUID(), "Gala restauré", NOW.minusSeconds(7200),
                NOW.minusSeconds(3600), "Nouveau bar");
        ShopReservationSlot slot = eventSlot(event);
        slot.setId(id);
        slot.setActive(false);
        slot.setEventStartsAtSnapshot(NOW.minusSeconds(20_000));
        slot.setEventEndsAtSnapshot(NOW.minusSeconds(19_000));
        slot.setEventLocationSnapshot("Ancien bar");
        when(slotRepository.findWithEventAndSeriesForUpdateById(id)).thenReturn(Optional.of(slot));

        ShopReservationSlotDto result = service.activate(id);

        assertThat(result.active()).isTrue();
        assertThat(result.location()).isEqualTo("Nouveau bar");
        assertThat(slot.getEventStartsAtSnapshot()).isEqualTo(event.getStartsAt());
        assertThat(slot.getEventEndsAtSnapshot()).isEqualTo(event.getEndsAt());
        assertThat(slot.getEventLocationSnapshot()).isEqualTo("Nouveau bar");
        verify(slotRepository).save(slot);
    }

    @Test
    void activateRejectsEventWithoutSourceOrValidPickupData() {
        UUID deletedId = UUID.randomUUID();
        ShopReservationSlot deletedSource = eventSlot(event(UUID.randomUUID(), "Gala", NOW, NOW.plusSeconds(1), "Hall"));
        deletedSource.setEvent(null);
        deletedSource.setActive(false);
        when(slotRepository.findWithEventAndSeriesForUpdateById(deletedId)).thenReturn(Optional.of(deletedSource));

        assertThatThrownBy(() -> service.activate(deletedId))
                .isInstanceOfSatisfying(CodedBadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SHOP_SLOT_ACTIVATION_INVALID"))
                .hasMessageContaining("source");

        UUID invalidId = UUID.randomUUID();
        ShopReservationSlot invalidEvent = eventSlot(event(UUID.randomUUID(), "Gala", NOW, null, "Hall"));
        invalidEvent.setActive(false);
        when(slotRepository.findWithEventAndSeriesForUpdateById(invalidId)).thenReturn(Optional.of(invalidEvent));
        assertThatThrownBy(() -> service.activate(invalidId))
                .isInstanceOf(CodedBadRequestException.class)
                .hasMessageContaining("horaires");
    }

    @Test
    void activateAllowsValidPastCustomSlotButRejectsInvalidCustomData() {
        UUID validId = UUID.randomUUID();
        ShopReservationSlot valid = custom(NOW.minusSeconds(7200), false);
        when(slotRepository.findWithEventAndSeriesForUpdateById(validId)).thenReturn(Optional.of(valid));

        assertThat(service.activate(validId).active()).isTrue();

        UUID invalidId = UUID.randomUUID();
        ShopReservationSlot invalid = custom(NOW.plusSeconds(60), false);
        invalid.setCustomLocation(" ");
        when(slotRepository.findWithEventAndSeriesForUpdateById(invalidId)).thenReturn(Optional.of(invalid));
        assertThatThrownBy(() -> service.activate(invalidId))
                .isInstanceOf(CodedBadRequestException.class)
                .hasMessageContaining("personnalisé");
    }

    @Test
    void deleteSeriesDeletesUnreferencedOccurrencesAndDeactivatesReferencedOnes() {
        UUID seriesId = UUID.randomUUID();
        ShopReservationSeries series = new ShopReservationSeries();
        series.setId(seriesId);
        ShopReservationSlot referenced = custom(NOW.plusSeconds(60), true);
        ShopReservationSlot removable = custom(NOW.plusSeconds(120), true);
        when(seriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(slotRepository.findBySeriesIdForUpdate(seriesId)).thenReturn(List.of(referenced, removable));
        when(orderRepository.existsByReservationSlotId(referenced.getId())).thenReturn(true);
        when(orderRepository.existsByReservationSlotId(removable.getId())).thenReturn(false);

        service.deleteSeries(seriesId);

        assertThat(referenced.isActive()).isFalse();
        verify(slotRepository).save(referenced);
        verify(slotRepository).delete(removable);
        verify(seriesRepository).delete(series);
    }

    @Test
    void settingsAreSingletonAndRejectNegativeDelay() {
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        assertThat(service.getSettings().lockDelayMinutes()).isEqualTo(1_440);

        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(new ShopSettings()));
        assertThat(service.updateSettings(new ShopSettingsRequest(90, null, null)).lockDelayMinutes()).isEqualTo(90);
        assertThatThrownBy(() -> service.updateSettings(new ShopSettingsRequest(-1, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    private static Event event(UUID id, String title, Instant startsAt, Instant endsAt, String location) {
        Event event = new Event();
        event.setId(id); event.setTitle(title); event.setStartsAt(startsAt); event.setEndsAt(endsAt); event.setLocation(location);
        return event;
    }

    private static ShopReservationSlot eventSlot(Event event) {
        ShopReservationSlot slot = new ShopReservationSlot();
        slot.setId(UUID.randomUUID()); slot.setType(ShopReservationSlotType.EVENT); slot.setEvent(event); slot.setActive(true);
        slot.setEventStartsAtSnapshot(event.getStartsAt()); slot.setEventEndsAtSnapshot(event.getEndsAt()); slot.setEventLocationSnapshot(event.getLocation());
        return slot;
    }

    private static ShopReservationSlot custom(Instant startsAt, boolean active) {
        ShopReservationSlot slot = new ShopReservationSlot();
        slot.setId(UUID.randomUUID()); slot.setType(ShopReservationSlotType.CUSTOM); slot.setActive(active);
        slot.setCustomStartsAt(startsAt); slot.setCustomEndsAt(startsAt.plusSeconds(3600)); slot.setCustomLocation("Kfet");
        return slot;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<List<ShopReservationSlot>> slotListCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
