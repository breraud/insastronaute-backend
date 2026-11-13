package fr.insastronaute.webapi.event.repository;

import fr.insastronaute.webapi.event.domain.EventRegistration;
import fr.insastronaute.webapi.event.domain.EventRegistrationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRegistrationRepository extends JpaRepository<EventRegistration, UUID> {

    Optional<EventRegistration> findByEventIdAndUserId(UUID eventId, UUID userId);

    long countByEventIdAndStatus(UUID eventId, EventRegistrationStatus status);

    @EntityGraph(attributePaths = {"event", "event.article", "event.categories", "event.targetDepartments"})
    List<EventRegistration> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = "user")
    List<EventRegistration> findByEventIdOrderByStatusAscCreatedAtAsc(UUID eventId);

    @EntityGraph(attributePaths = "user")
    List<EventRegistration> findByEventIdAndStatusOrderByCreatedAtAsc(UUID eventId, EventRegistrationStatus status);

    @EntityGraph(attributePaths = {"event", "user"})
    List<EventRegistration> findByEventIdInAndUserIdIn(Collection<UUID> eventIds, Collection<UUID> userIds);

    /**
     * Les inscriptions d'un joueur sur une page d'evenements, en une requete.
     *
     * <p>La page lisait le statut du joueur evenement par evenement. C'est la meme
     * linearite que {@code statusCountsByEventIds} evite deja pour les decomptes, et
     * elle n'avait pas de raison de subsister ici.</p>
     */
    List<EventRegistration> findByUserIdAndEventIdIn(UUID userId, Collection<UUID> eventIds);

    Optional<EventRegistration> findFirstByEventIdAndStatusOrderByCreatedAtAscIdAsc(
            UUID eventId,
            EventRegistrationStatus status
    );

    @Query("""
            select registration.event.id, registration.status, count(registration)
            from EventRegistration registration
            where registration.event.id in :eventIds
            group by registration.event.id, registration.status
            """)
    List<Object[]> statusCountsByEventIds(@Param("eventIds") Collection<UUID> eventIds);

    @Query("""
            select registration.event.id,
                   sum(case when registration.attended = true then 1L else 0L end),
                   sum(case when registration.attended is null then 1L else 0L end)
            from EventRegistration registration
            where registration.event.id in :eventIds
            group by registration.event.id
            """)
    List<Object[]> attendanceCountsByEventIds(@Param("eventIds") Collection<UUID> eventIds);

    // ---- Achievement key phrases -------------------------------------------

    @Query("""
            select count(registration)
            from EventRegistration registration
            where registration.user.id = :userId
              and registration.attended = true
              and registration.event.source = fr.insastronaute.webapi.event.domain.EventSource.TOURNAMENT
            """)
    long countTournamentsAttendedByUser(@Param("userId") UUID userId);

    @Query("""
            select registration.user.id
            from EventRegistration registration
            where registration.attended = true
              and registration.event.source = fr.insastronaute.webapi.event.domain.EventSource.TOURNAMENT
              and (:afterUserId is null or registration.user.id > :afterUserId)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = registration.user.id
                      and awarded.status = 'VALIDATED'
                )
            group by registration.user.id
            having count(registration) >= :threshold
            order by registration.user.id asc
            """)
    List<UUID> findUsersAttendingTournaments(
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    @Query("""
            select count(registration)
            from EventRegistration registration
            where registration.user.id = :userId
            """)
    long countRegistrationsByUser(@Param("userId") UUID userId);

    @Query("""
            select registration.user.id
            from EventRegistration registration
            where (:afterUserId is null or registration.user.id > :afterUserId)
            and not exists (
                  select 1 from ChallengeSubmission awarded
                  where awarded.challenge.id = :challengeId
                    and awarded.user.id = registration.user.id
                    and awarded.status = 'VALIDATED'
              )
            group by registration.user.id
            having count(registration) >= :threshold
            order by registration.user.id asc
            """)
    List<UUID> findUsersWithEnoughRegistrations(
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );
}
