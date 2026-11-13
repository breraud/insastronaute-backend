package fr.insastronaute.webapi.push.repository;

import fr.insastronaute.webapi.push.domain.PushOutboxEntry;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PushOutboxRepository extends JpaRepository<PushOutboxEntry, Long> {

    /** Les plus anciennes d'abord : une notification en retard vaut mieux qu'une notification perdue. */
    List<PushOutboxEntry> findByOrderByCreatedAtAsc(Limit limit);
}
