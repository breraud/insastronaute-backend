package fr.insastronaute.webapi.notification.service;

import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.notification.domain.Notification;
import fr.insastronaute.webapi.push.service.PushEnqueuer;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.dto.NotificationDto;
import fr.insastronaute.webapi.notification.repository.NotificationRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PushEnqueuer pushEnqueuer;
    /**
     * Le courtier qui porte deja la fresque et les combats.
     *
     * <p>Optionnel : les tests unitaires du service n'ont pas de courtier, et une
     * notification enregistree sans etre annoncee reste une notification.</p>
     */
    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            PushEnqueuer pushEnqueuer
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.pushEnqueuer = pushEnqueuer;
    }

    @Transactional
    public NotificationDto notifyUser(
            UUID recipientUserId,
            NotificationType type,
            String title,
            String body,
            Map<String, Object> payload
    ) {
        return notifyUser(recipientUserId, null, type, title, body, payload, null, null);
    }

    @Transactional
    public NotificationDto notifyUser(
            UUID recipientUserId,
            UUID actorUserId,
            NotificationType type,
            String title,
            String body,
            Map<String, Object> payload,
            String sourceType,
            UUID sourceId
    ) {
        AppUser recipient = findUser(recipientUserId);
        AppUser actor = actorUserId == null ? null : findUser(actorUserId);

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setActor(actor);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setPayload(payload);
        notification.setSourceType(sourceType);
        notification.setSourceId(sourceId);

        Notification saved = notificationRepository.save(notification);
        // L'envoi ne part pas d'ici : on depose une ligne dans la file, dans cette
        // transaction. Seize appelants ecrivent des notifications au milieu de gestes
        // qui comptent - un succes accorde, un defi valide, un evenement annule - et
        // aucun ne doit pouvoir echouer parce qu'un service de push est injoignable.
        pushEnqueuer.enqueue(saved.getId());
        announce(saved, recipient);
        return NotificationDto.from(saved);
    }

    /**
     * Prevenir l'application ouverte, tout de suite.
     *
     * <p>Le push couvre l'application fermee ; il ne dit rien a quelqu'un qui a
     * l'ecran sous les yeux. La cloche ne bougeait alors qu'au rechargement de la
     * page -- une invitation recue pendant qu'on regarde restait invisible.</p>
     *
     * <p>Le courtier est celui qui porte deja la fresque et les combats, et la cle
     * d'adressage est la meme qu'eux : le sub Keycloak. Un echec ici ne remonte
     * jamais : la notification est enregistree, c'est ce qui compte. Le joueur la
     * verra a sa prochaine lecture.</p>
     */
    private void announce(Notification saved, AppUser recipient) {
        if (messagingTemplate == null || recipient.getKeycloakSub() == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(
                    recipient.getKeycloakSub(),
                    "/queue/notifications",
                    NotificationDto.from(saved)
            );
        } catch (RuntimeException unreachable) {
            LOGGER.warn("Notification {} enregistree, mais non annoncee en direct.", saved.getId(), unreachable);
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> listMine(AppUser currentUser, boolean unreadOnly, Pageable pageable) {
        Page<Notification> notifications = unreadOnly
                ? notificationRepository.findByRecipientAndReadAtIsNull(currentUser, pageable)
                : notificationRepository.findByRecipient(currentUser, pageable);

        return notifications.map(NotificationDto::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(AppUser currentUser) {
        return notificationRepository.countByRecipientAndReadAtIsNull(currentUser);
    }

    @Transactional
    public NotificationDto markAsRead(AppUser currentUser, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipient(notificationId, currentUser)
                .orElseThrow(() -> new NotFoundException("Notification introuvable."));

        if (notification.getReadAt() == null) {
            notification.setReadAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        }

        return NotificationDto.from(notificationRepository.save(notification));
    }

    @Transactional
    public void markAllAsRead(AppUser currentUser) {
        notificationRepository.markAllUnreadAsRead(currentUser, fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
    }

    private AppUser findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
    }
}
