package fr.insastronaute.webapi.event.repository;

import fr.insastronaute.webapi.event.domain.Event;
import fr.insastronaute.webapi.event.domain.EventRegistration;
import fr.insastronaute.webapi.event.domain.EventSource;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class EventRegistrationRepositoryTest {

    @Autowired EventRepository eventRepository;
    @Autowired EventRegistrationRepository registrationRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void waitlistUsesRegistrationIdToBreakEqualCreationTimeTies() {
        Event event = eventRepository.saveAndFlush(event());
        AppUser firstUser = userRepository.saveAndFlush(user("first"));
        AppUser secondUser = userRepository.saveAndFlush(user("second"));
        Instant sameTime = Instant.parse("2026-09-06T12:00:00Z");
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        insertWaiting(secondId, event.getId(), secondUser.getId(), sameTime);
        insertWaiting(firstId, event.getId(), firstUser.getId(), sameTime);

        EventRegistration promoted = registrationRepository
                .findFirstByEventIdAndStatusOrderByCreatedAtAscIdAsc(
                        event.getId(), fr.insastronaute.webapi.event.domain.EventRegistrationStatus.WAITLISTED)
                .orElseThrow();

        assertThat(promoted.getId()).isEqualTo(firstId);
    }

    private void insertWaiting(UUID id, UUID eventId, UUID userId, Instant createdAt) {
        Timestamp timestamp = Timestamp.from(createdAt);
        jdbcTemplate.update("""
                insert into event_registrations (id, event_id, user_id, status, created_at, updated_at)
                values (?, ?, ?, 'WAITLISTED', ?, ?)
                """, id, eventId, userId, timestamp, timestamp);
    }

    private static Event event() {
        Event event = new Event();
        event.setTitle("File déterministe");
        event.setShortDescription("Test");
        event.setSource(EventSource.GLOBAL);
        event.setStartsAt(Instant.parse("2026-09-07T12:00:00Z"));
        return event;
    }

    private static AppUser user(String suffix) {
        AppUser user = new AppUser();
        user.setKeycloakSub("sub-" + suffix);
        user.setEmail(suffix + "@example.com");
        user.setDisplayName(suffix);
        return user;
    }
}
