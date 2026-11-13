package fr.insastronaute.webapi.notification.repository;

import fr.insastronaute.webapi.notification.domain.Notification;
import fr.insastronaute.webapi.user.domain.AppUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipient(AppUser recipient, Pageable pageable);

    Page<Notification> findByRecipientAndReadAtIsNull(AppUser recipient, Pageable pageable);

    long countByRecipientAndReadAtIsNull(AppUser recipient);

    Optional<Notification> findByIdAndRecipient(UUID id, AppUser recipient);

    @Modifying
    @Query("""
            update Notification notification
            set notification.readAt = :readAt
            where notification.recipient = :recipient
            and notification.readAt is null
            """)
    int markAllUnreadAsRead(@Param("recipient") AppUser recipient, @Param("readAt") Instant readAt);
}
