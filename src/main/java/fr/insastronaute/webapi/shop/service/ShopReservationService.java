package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.event.domain.Event;
import fr.insastronaute.webapi.event.repository.EventRepository;
import fr.insastronaute.webapi.shop.domain.*;
import fr.insastronaute.webapi.shop.dto.*;
import fr.insastronaute.webapi.shop.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ShopReservationService {
    static final ZoneId SHOP_ZONE = ZoneId.of("Europe/Paris");

    private final ShopReservationSlotRepository slotRepository;
    private final ShopReservationSeriesRepository seriesRepository;
    private final ShopSettingsRepository settingsRepository;
    private final ShopOrderRepository orderRepository;
    private final EventRepository eventRepository;
    private final Clock clock;

    public ShopReservationService(
            ShopReservationSlotRepository slotRepository,
            ShopReservationSeriesRepository seriesRepository,
            ShopSettingsRepository settingsRepository,
            ShopOrderRepository orderRepository,
            EventRepository eventRepository,
            Clock clock
    ) {
        this.slotRepository = slotRepository;
        this.seriesRepository = seriesRepository;
        this.settingsRepository = settingsRepository;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ShopReservationSlotDto> listAvailable() {
        Instant now = clock.instant();
        return slotRepository.findAllWithEventAndSeries().stream()
                .filter(ShopReservationSlot::isActive)
                .filter(slot -> effectiveStart(slot).isAfter(now))
                .sorted(Comparator.comparing(this::effectiveStart))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShopReservationSlotDto> listAdmin() {
        return slotRepository.findAllWithEventAndSeries().stream()
                .sorted(Comparator.comparing(this::effectiveStart))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShopReservationEventDto> listEventOptions() {
        return eventRepository.findAllByOrderByStartsAtAscCreatedAtDesc().stream()
                .filter(this::isImportableEvent)
                .map(event -> new ShopReservationEventDto(
                        event.getId(), event.getTitle(), event.getStartsAt(), event.getEndsAt(), event.getLocation()))
                .toList();
    }

    @Transactional
    public ShopReservationSlotDto createEvent(ShopEventReservationRequest request) {
        if (request == null || request.eventId() == null) {
            throw invalid("eventId", "L'événement est obligatoire.");
        }
        Event event = eventRepository.findWithLockById(request.eventId())
                .orElseThrow(() -> new NotFoundException("Événement introuvable."));
        validateEvent(event);
        if (slotRepository.existsByEventId(event.getId())) {
            throw invalid("eventId", "Cet événement a déjà été importé.");
        }

        ShopReservationSlot slot = new ShopReservationSlot();
        slot.setType(ShopReservationSlotType.EVENT);
        slot.setEvent(event);
        slot.setEventStartsAtSnapshot(event.getStartsAt());
        slot.setEventEndsAtSnapshot(event.getEndsAt());
        slot.setEventLocationSnapshot(event.getLocation());
        slot.setNote(normalize(request.note()));
        slot.setActive(true);
        return toDto(slotRepository.save(slot));
    }

    @Transactional
    public ShopReservationSlotDto createCustom(ShopCustomReservationRequest request) {
        validateCustom(request == null ? null : request.startsAt(), request == null ? null : request.endsAt(),
                request == null ? null : request.location());
        ShopReservationSlot slot = newCustomSlot(request.startsAt(), request.endsAt(), request.location(), request.note(), null);
        return toDto(slotRepository.save(slot));
    }

    @Transactional
    public List<ShopReservationSlotDto> createRecurring(ShopRecurringReservationRequest request) {
        validateRecurring(request);

        LocalDate first = request.startsOn().with(TemporalAdjusters.nextOrSame(request.dayOfWeek()));
        if (first.isAfter(request.endsOn())) {
            throw new BadRequestException("La période ne contient aucune occurrence.");
        }
        long occurrenceCount = ChronoUnit.WEEKS.between(first, request.endsOn()) + 1;
        if (occurrenceCount > 366) {
            throw new BadRequestException("Une série ne peut pas contenir plus de 366 occurrences.");
        }
        for (long index = 0; index < occurrenceCount; index++) {
            validateUnambiguousParisTime(first.plusWeeks(index), request.startsAt());
            validateUnambiguousParisTime(first.plusWeeks(index), request.endsAt());
        }

        ShopReservationSeries series = new ShopReservationSeries();
        series.setStartsOn(request.startsOn());
        series.setEndsOn(request.endsOn());
        series.setDayOfWeek(request.dayOfWeek().getValue());
        series.setStartsAt(request.startsAt());
        series.setEndsAt(request.endsAt());
        series.setLocation(normalizeRequired(request.location()));
        series.setNote(normalize(request.note()));
        series = seriesRepository.save(series);

        var occurrences = new java.util.ArrayList<ShopReservationSlot>();
        for (LocalDate date = first; !date.isAfter(request.endsOn()); date = date.plusWeeks(1)) {
            Instant startsAt = date.atTime(request.startsAt()).atZone(SHOP_ZONE).toInstant();
            Instant endsAt = date.atTime(request.endsAt()).atZone(SHOP_ZONE).toInstant();
            occurrences.add(newCustomSlot(startsAt, endsAt, request.location(), request.note(), series));
        }
        slotRepository.saveAll(occurrences);
        return occurrences.stream().map(this::toDto).toList();
    }

    @Transactional
    public ShopReservationSlotDto update(UUID id, ShopReservationSlotUpdateRequest request) {
        ShopReservationSlot slot = findSlot(id);
        if (request == null) {
            throw new BadRequestException("Les modifications sont obligatoires.");
        }
        if (slot.getType() == ShopReservationSlotType.EVENT) {
            if (request.startsAt() != null || request.endsAt() != null || request.location() != null) {
                throw new BadRequestException("Les horaires et le lieu d'un événement restent synchronisés avec l'événement source.");
            }
        } else {
            Instant startsAt = request.startsAt() == null ? slot.getCustomStartsAt() : request.startsAt();
            Instant endsAt = request.endsAt() == null ? slot.getCustomEndsAt() : request.endsAt();
            String location = request.location() == null ? slot.getCustomLocation() : request.location();
            validateCustom(startsAt, endsAt, location);
            slot.setCustomStartsAt(startsAt);
            slot.setCustomEndsAt(endsAt);
            slot.setCustomLocation(normalizeRequired(location));
        }
        slot.setNote(normalize(request.note()));
        return toDto(slotRepository.save(slot));
    }

    @Transactional
    public ShopReservationSlotDto deactivate(UUID id) {
        ShopReservationSlot slot = findSlot(id);
        slot.setActive(false);
        return toDto(slotRepository.save(slot));
    }

    @Transactional
    public ShopReservationSlotDto activate(UUID id) {
        ShopReservationSlot slot = slotRepository.findWithEventAndSeriesForUpdateById(id)
                .orElseThrow(() -> new NotFoundException("Créneau introuvable."));
        if (slot.getType() == ShopReservationSlotType.EVENT) {
            Event event = slot.getEvent();
            if (event == null) {
                throw activationInvalid("Impossible de réactiver ce créneau : son événement source a été supprimé.");
            }
            if (event.getStartsAt() == null || event.getEndsAt() == null
                    || !event.getEndsAt().isAfter(event.getStartsAt())) {
                throw activationInvalid("Impossible de réactiver ce créneau : les horaires de l'événement sont invalides.");
            }
            if (event.getLocation() == null || event.getLocation().isBlank()) {
                throw activationInvalid("Impossible de réactiver ce créneau : le lieu de l'événement est invalide.");
            }
            slot.setEventStartsAtSnapshot(event.getStartsAt());
            slot.setEventEndsAtSnapshot(event.getEndsAt());
            slot.setEventLocationSnapshot(event.getLocation());
        } else if (slot.getCustomStartsAt() == null || slot.getCustomEndsAt() == null
                || !slot.getCustomEndsAt().isAfter(slot.getCustomStartsAt())
                || slot.getCustomLocation() == null || slot.getCustomLocation().isBlank()) {
            throw activationInvalid("Impossible de réactiver ce créneau personnalisé : ses données sont invalides.");
        }
        slot.setActive(true);
        return toDto(slotRepository.save(slot));
    }

    @Transactional
    public void deleteSeries(UUID seriesId) {
        ShopReservationSeries series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new NotFoundException("Série de créneaux introuvable."));
        for (ShopReservationSlot slot : slotRepository.findBySeriesIdForUpdate(seriesId)) {
            if (orderRepository.existsByReservationSlotId(slot.getId())) {
                slot.setActive(false);
                slotRepository.save(slot);
            } else {
                slotRepository.delete(slot);
            }
        }
        seriesRepository.delete(series);
    }

    @Transactional
    public ShopSettingsDto getSettings() {
        ShopSettings settings = settingsRepository.findById(ShopSettings.SINGLETON_ID)
                .orElseGet(() -> settingsRepository.save(new ShopSettings()));
        return toDto(settings);
    }

    @Transactional
    public ShopSettingsDto updateSettings(ShopSettingsRequest request) {
        if (request == null || request.lockDelayMinutes() == null || request.lockDelayMinutes() < 0) {
            throw invalid("lockDelayMinutes", "Le délai de verrouillage doit être positif ou nul.");
        }
        // Null vaut « pas de limite », et c'est la seule facon de la retirer une fois
        // posee. Zero serait une boutique fermee sans le dire : il n'est pas accepte.
        if (request.maxOrdersPerUser() != null && request.maxOrdersPerUser() < 1) {
            throw invalid("maxOrdersPerUser", "La limite de précommandes doit valoir au moins 1.");
        }
        if (request.maxItemsPerOrder() != null && request.maxItemsPerOrder() < 1) {
            throw invalid("maxItemsPerOrder", "La limite d'articles doit valoir au moins 1.");
        }
        ShopSettings settings = settingsRepository.findById(ShopSettings.SINGLETON_ID).orElseGet(ShopSettings::new);
        settings.setLockDelayMinutes(request.lockDelayMinutes());
        settings.setMaxOrdersPerUser(request.maxOrdersPerUser());
        settings.setMaxItemsPerOrder(request.maxItemsPerOrder());
        settingsRepository.save(settings);
        return toDto(settings);
    }

    private ShopSettingsDto toDto(ShopSettings settings) {
        return new ShopSettingsDto(settings.getLockDelayMinutes(), settings.getMaxOrdersPerUser(),
                settings.getMaxItemsPerOrder());
    }

    public ShopReservationSlotDto toDto(ShopReservationSlot slot) {
        Event event = slot.getEvent();
        boolean eventSlot = slot.getType() == ShopReservationSlotType.EVENT;
        Instant startsAt = eventSlot && event != null ? event.getStartsAt()
                : eventSlot ? slot.getEventStartsAtSnapshot() : slot.getCustomStartsAt();
        Instant endsAt = eventSlot && event != null ? event.getEndsAt()
                : eventSlot ? slot.getEventEndsAtSnapshot() : slot.getCustomEndsAt();
        String location = eventSlot && event != null ? event.getLocation()
                : eventSlot ? slot.getEventLocationSnapshot() : slot.getCustomLocation();
        return new ShopReservationSlotDto(
                slot.getId(), slot.getType(), event == null ? null : event.getId(), event == null ? null : event.getTitle(),
                slot.getSeries() == null ? null : slot.getSeries().getId(), startsAt, endsAt, location,
                slot.getNote(), slot.isActive());
    }

    private ShopReservationSlot findSlot(UUID id) {
        if (id == null) throw new NotFoundException("Créneau introuvable.");
        return slotRepository.findWithEventAndSeriesById(id)
                .orElseThrow(() -> new NotFoundException("Créneau introuvable."));
    }

    private ShopReservationSlot newCustomSlot(Instant startsAt, Instant endsAt, String location, String note,
                                               ShopReservationSeries series) {
        ShopReservationSlot slot = new ShopReservationSlot();
        slot.setType(ShopReservationSlotType.CUSTOM);
        slot.setSeries(series);
        slot.setCustomStartsAt(startsAt);
        slot.setCustomEndsAt(endsAt);
        slot.setCustomLocation(normalizeRequired(location));
        slot.setNote(normalize(note));
        slot.setActive(true);
        return slot;
    }

    private Instant effectiveStart(ShopReservationSlot slot) {
        if (slot.getType() == ShopReservationSlotType.CUSTOM) return slot.getCustomStartsAt();
        return slot.getEvent() == null ? slot.getEventStartsAtSnapshot() : slot.getEvent().getStartsAt();
    }

    private void validateRecurring(ShopRecurringReservationRequest request) {
        if (request == null || request.startsOn() == null || request.endsOn() == null || request.dayOfWeek() == null
                || request.startsAt() == null || request.endsAt() == null) {
            throw new BadRequestException("La période et les horaires de la série sont obligatoires.");
        }
        if (request.endsOn().isBefore(request.startsOn())) {
            throw invalid("endsOn", "La date de fin doit suivre la date de début.");
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw invalid("endsAt", "L'heure de fin doit suivre l'heure de début.");
        }
        if (normalize(request.location()) == null) {
            throw invalid("location", "Le lieu est obligatoire.");
        }
    }

    private void validateEvent(Event event) {
        if (event.getStartsAt() == null || event.getEndsAt() == null) {
            throw new BadRequestException("Les horaires de l'événement doivent être complets.");
        }
        if (!event.getEndsAt().isAfter(event.getStartsAt())) {
            throw invalid("eventId", "La fin de l'événement doit suivre son début.");
        }
        if (normalize(event.getLocation()) == null) {
            throw invalid("eventId", "L'événement doit avoir un lieu.");
        }
    }

    private boolean isImportableEvent(Event event) {
        return event.getStartsAt() != null
                && event.getEndsAt() != null
                && event.getEndsAt().isAfter(event.getStartsAt())
                && normalize(event.getLocation()) != null;
    }

    private void validateUnambiguousParisTime(LocalDate date, LocalTime time) {
        if (SHOP_ZONE.getRules().getValidOffsets(LocalDateTime.of(date, time)).size() != 1) {
            throw new BadRequestException("Une occurrence tombe pendant un changement d'heure Europe/Paris.");
        }
    }

    private void validateCustom(Instant startsAt, Instant endsAt, String location) {
        if (startsAt == null || endsAt == null) {
            throw new BadRequestException("Les horaires du créneau sont obligatoires.");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw invalid("endsAt", "La fin du créneau doit suivre son début.");
        }
        if (normalize(location) == null) {
            throw invalid("location", "Le lieu est obligatoire.");
        }
    }

    private static String normalizeRequired(String value) {
        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static BadRequestException invalid(String field, String message) {
        return new BadRequestException(message, field, message);
    }

    private static CodedBadRequestException activationInvalid(String message) {
        return new CodedBadRequestException("SHOP_SLOT_ACTIVATION_INVALID", message);
    }
}
