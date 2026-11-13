package fr.insastronaute.webapi.notification.service;

import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.notification.domain.Notification;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.dto.NotificationDto;
import fr.insastronaute.webapi.notification.repository.NotificationRepository;
import fr.insastronaute.webapi.push.service.PushEnqueuer;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private PushEnqueuer pushEnqueuer;
    private SimpMessagingTemplate messagingTemplate;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        pushEnqueuer = mock(PushEnqueuer.class);
        notificationService = new NotificationService(notificationRepository, userRepository, pushEnqueuer);
        // Le courtier est injecté par champ en production : une notification
        // enregistrée sans être annoncée reste une notification.
        messagingTemplate = mock(SimpMessagingTemplate.class);
        ReflectionTestUtils.setField(notificationService, "messagingTemplate", messagingTemplate);
    }

    /**
     * Le push couvre l'application fermée ; il ne dit rien à quelqu'un qui a l'écran
     * sous les yeux. La cloche ne bougeait alors qu'au rechargement de la page.
     */
    @Test
    void notifyUserAnnouncesToTheOpenApplication() {
        UUID recipientId = UUID.randomUUID();
        AppUser recipient = new AppUser();
        recipient.setId(recipientId);
        recipient.setKeycloakSub("recipient-sub");
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.notifyUser(recipientId, NotificationType.PUSH_TEST, "Titre", "Corps", Map.of());

        // Même clé d'adressage que la fresque et les combats : le sub Keycloak.
        verify(messagingTemplate).convertAndSendToUser(
                eq("recipient-sub"), eq("/queue/notifications"), any(Object.class));
    }

    /** Un courtier injoignable ne doit jamais emporter l'enregistrement. */
    @Test
    void anUnreachableBrokerNeverLosesTheNotification() {
        UUID recipientId = UUID.randomUUID();
        AppUser recipient = new AppUser();
        recipient.setId(recipientId);
        recipient.setKeycloakSub("recipient-sub");
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("courtier injoignable"))
                .when(messagingTemplate)
                .convertAndSendToUser(anyString(), anyString(), any(Object.class));

        NotificationDto saved = notificationService.notifyUser(
                recipientId, NotificationType.PUSH_TEST, "Titre", "Corps", Map.of());

        assertThat(saved).isNotNull();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyUserPersistsNotificationWithPayloadAndActor() {
        AppUser recipient = user(UUID.randomUUID());
        AppUser actor = user(UUID.randomUUID());
        UUID sourceId = UUID.randomUUID();

        when(userRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDto result = notificationService.notifyUser(
                recipient.getId(),
                actor.getId(),
                NotificationType.FRIEND_REQUEST_RECEIVED,
                "Nouvelle demande",
                "Alice veut t'ajouter.",
                Map.of("friendRequestId", "request-1"),
                "FRIEND_REQUEST",
                sourceId
        );

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());

        Notification notification = notificationCaptor.getValue();
        assertThat(notification.getRecipient()).isEqualTo(recipient);
        assertThat(notification.getActor()).isEqualTo(actor);
        assertThat(notification.getPayload()).containsEntry("friendRequestId", "request-1");
        assertThat(notification.getSourceType()).isEqualTo("FRIEND_REQUEST");
        assertThat(notification.getSourceId()).isEqualTo(sourceId);
        assertThat(result.type()).isEqualTo(NotificationType.FRIEND_REQUEST_RECEIVED);
    }

    @Test
    void listMineReturnsOnlyUnreadWhenRequested() {
        AppUser user = user(UUID.randomUUID());
        PageRequest pageable = PageRequest.of(0, 20);
        Notification unread = notification(user, null);

        when(notificationRepository.findByRecipientAndReadAtIsNull(user, pageable))
                .thenReturn(new PageImpl<>(List.of(unread)));

        assertThat(notificationService.listMine(user, true, pageable).getContent())
                .hasSize(1)
                .first()
                .extracting(NotificationDto::readAt)
                .isNull();
    }

    @Test
    void markAsReadSetsReadAtWhenNotificationBelongsToUser() {
        AppUser user = user(UUID.randomUUID());
        UUID notificationId = UUID.randomUUID();
        Notification notification = notification(user, null);

        when(notificationRepository.findByIdAndRecipient(notificationId, user)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDto result = notificationService.markAsRead(user, notificationId);

        assertThat(result.readAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsReadRejectsNotificationFromAnotherUser() {
        AppUser user = user(UUID.randomUUID());
        UUID notificationId = UUID.randomUUID();

        when(notificationRepository.findByIdAndRecipient(notificationId, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(user, notificationId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Notification introuvable.");
    }

    @Test
    void countUnreadDelegatesToRepository() {
        AppUser user = user(UUID.randomUUID());

        when(notificationRepository.countByRecipientAndReadAtIsNull(user)).thenReturn(3L);

        assertThat(notificationService.countUnread(user)).isEqualTo(3L);
    }

    private static AppUser user(UUID id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setKeycloakSub(id.toString());
        user.setEmail(id + "@example.com");
        return user;
    }

    private static Notification notification(AppUser recipient, Instant readAt) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipient(recipient);
        notification.setType(NotificationType.GROUP_INVITE_RECEIVED);
        notification.setTitle("Invitation");
        notification.setBody("Tu as recu une invitation.");
        notification.setPayload(Map.of("groupId", UUID.randomUUID().toString()));
        notification.setReadAt(readAt);
        return notification;
    }

    /**
     * L'ecriture depose l'envoi dans la file, et n'appelle rien.
     *
     * <p>C'est la garantie du chantier : seize appelants ecrivent des notifications au
     * milieu de gestes qui comptent - un succes accorde, un defi valide, un evenement
     * annule - et aucun ne doit pouvoir echouer ni ralentir parce qu'un service de push
     * est injoignable. Le reseau n'intervient que dans le depileur.</p>
     */
    @Test
    void writingANotificationOnlyQueuesTheSendAndNeverCallsOut() {
        AppUser recipient = user(UUID.randomUUID());
        when(userRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.notifyUser(
                recipient.getId(), NotificationType.CHALLENGE_SUBMISSION_VALIDATED, "Défi validé", "Corps", Map.of());

        verify(pushEnqueuer).enqueue(any());
    }
}
