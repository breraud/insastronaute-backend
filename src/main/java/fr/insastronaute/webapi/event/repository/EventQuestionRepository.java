package fr.insastronaute.webapi.event.repository;

import fr.insastronaute.webapi.event.domain.EventQuestion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventQuestionRepository extends JpaRepository<EventQuestion, UUID> {

    @EntityGraph(attributePaths = "options")
    List<EventQuestion> findByEventIdOrderByPositionAsc(UUID eventId);

    @EntityGraph(attributePaths = "options")
    List<EventQuestion> findByEventIdInOrderByPositionAsc(Collection<UUID> eventIds);

    long countByEventId(UUID eventId);

    /** Ids of the events that ask something, in one query for a whole listing. */
    @Query("select distinct question.event.id from EventQuestion question where question.event.id in :eventIds")
    List<UUID> findEventIdsWithQuestions(@Param("eventIds") Collection<UUID> eventIds);
}
