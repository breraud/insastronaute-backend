package fr.insastronaute.webapi.sts.multiplayer.repository;

import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpNotification;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpNotificationType;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StsPvpNotificationRepository extends JpaRepository<StsPvpNotification, UUID> {

    List<StsPvpNotification> findByRecipientOrderByCreatedAtDesc(AppUser recipient);

    Optional<StsPvpNotification> findByIdAndRecipient(UUID id, AppUser recipient);

    Optional<StsPvpNotification> findFirstByRecipientAndTypeAndReadAtIsNullOrderByCreatedAtDesc(
            AppUser recipient,
            StsPvpNotificationType type
    );
}
