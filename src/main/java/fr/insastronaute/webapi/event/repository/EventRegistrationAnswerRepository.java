package fr.insastronaute.webapi.event.repository;

import fr.insastronaute.webapi.event.domain.EventRegistrationAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventRegistrationAnswerRepository extends JpaRepository<EventRegistrationAnswer, UUID> {

    @EntityGraph(attributePaths = {"question", "option"})
    List<EventRegistrationAnswer> findByRegistrationId(UUID registrationId);

    /** Choice tallies for one event: question, option, count. Drives the chart. */
    @Query("""
            select answer.question.id, answer.option.id, count(answer)
            from EventRegistrationAnswer answer
            where answer.question.event.id = :eventId
              and answer.option is not null
            group by answer.question.id, answer.option.id
            """)
    List<Object[]> choiceTallies(@Param("eventId") UUID eventId);

    /** Free-text answers for one event, newest first, with who wrote them. */
    @Query("""
            select answer
            from EventRegistrationAnswer answer
            join fetch answer.question
            join fetch answer.registration registration
            join fetch registration.user
            where answer.question.event.id = :eventId
              and answer.textValue is not null
            order by answer.updatedAt desc
            """)
    List<EventRegistrationAnswer> textAnswers(@Param("eventId") UUID eventId);

    /** How many registrations answered each question, to derive the unanswered count. */
    @Query("""
            select answer.question.id, count(distinct answer.registration.id)
            from EventRegistrationAnswer answer
            where answer.question.event.id = :eventId
            group by answer.question.id
            """)
    List<Object[]> answeredRegistrationCounts(@Param("eventId") UUID eventId);
}
