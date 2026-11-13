package fr.insastronaute.webapi.event.repository;

import fr.insastronaute.webapi.event.domain.Event;
import fr.insastronaute.webapi.event.domain.EventSource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    @EntityGraph(attributePaths = {"article", "categories", "targetDepartments"})
    List<Event> findAllByOrderByStartsAtAscCreatedAtDesc();

    @EntityGraph(attributePaths = {"article", "categories", "targetDepartments"})
    @Query("""
            select distinct event from Event event
            left join event.targetDepartments department
            where event.publishedAt is not null
            and event.publishedAt <= :now
            and (
                :departmentId is null
                or event.targetDepartments is empty
                or department.id = :departmentId
            )
            order by event.startsAt asc, event.createdAt desc
            """)
    List<Event> findVisibleForPlayer(@Param("departmentId") UUID departmentId, @Param("now") Instant now);

    @EntityGraph(attributePaths = {"article", "categories", "targetDepartments"})
    @Query("select event from Event event where event.id = :id")
    Optional<Event> findWithGraphById(@Param("id") UUID id);

    List<Event> findBySourceOrderByStartsAtDesc(EventSource source);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from Event event where event.id = :id")
    Optional<Event> findWithLockById(@Param("id") UUID id);
}
