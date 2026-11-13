package fr.insastronaute.webapi.push.repository;

import fr.insastronaute.webapi.push.domain.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserId(UUID userId);

    void deleteByEndpoint(String endpoint);

    long countByUserId(UUID userId);
}
