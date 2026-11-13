package fr.insastronaute.webapi.push.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interaso.webpush.WebPush;
import com.interaso.webpush.WebPushService;
import fr.insastronaute.webapi.notification.domain.Notification;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.repository.NotificationRepository;
import fr.insastronaute.webapi.push.domain.PushOutboxEntry;
import fr.insastronaute.webapi.push.domain.PushSubscription;
import fr.insastronaute.webapi.push.repository.PushOutboxRepository;
import fr.insastronaute.webapi.push.repository.PushSubscriptionRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le depileur, qui est le seul endroit du push ou le reseau intervient.
 *
 * <p>Trois issues par ligne, et une seule est un echec. Ce qui est eprouve ici est
 * surtout la deuxieme : sans la purge sur 404/410, la table d'abonnements gonfle de
 * telephones reinitialises que chaque envoi repaie, indefiniment.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushDispatcherTest {

    @Mock private PushOutboxRepository outbox;
    @Mock private PushSubscriptionRepository subscriptions;
    @Mock private NotificationRepository notifications;
    @Mock private WebPushService webPush;
    @Mock private PlatformTransactionManager transactionManager;

    private PushDispatcher dispatcher;
    private PushProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PushProperties();
        properties.setBatchSize(10);
        properties.setMaxAttempts(3);
        dispatcher = new PushDispatcher(outbox, subscriptions, notifications, webPush, properties,
                new ObjectMapper(), transactionManager);
    }

    @Test
    void anAcceptedSendClearsTheQueueEntryAndDatesTheDevice() {
        PushOutboxEntry entry = queued();
        PushSubscription device = device();
        whenNotificationExists(entry, device);
        when(webPush.send(any(String.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(WebPush.SubscriptionState.ACTIVE);

        dispatcher.dispatchPending();

        assertThat(device.getLastSuccessAt()).isNotNull();
        verify(subscriptions, never()).delete(device);
        verify(outbox).delete(entry);
    }

    /**
     * 404 et 410 disent que l'appareil n'existe plus. C'est la seule purge du systeme :
     * rien d'autre ne retire un abonnement mort, et sans elle chaque envoi paierait
     * pour des telephones reinitialises il y a des mois.
     */
    @Test
    void aGoneDeviceIsRemovedRatherThanRetriedForever() {
        PushOutboxEntry entry = queued();
        PushSubscription device = device();
        whenNotificationExists(entry, device);
        when(webPush.send(any(String.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(WebPush.SubscriptionState.EXPIRED);

        dispatcher.dispatchPending();

        verify(subscriptions).delete(device);
        // La ligne part quand meme : l'envoi n'a pas echoue, le destinataire a disparu.
        verify(outbox).delete(entry);
    }

    @Test
    void aFailedSendKeepsTheEntryAndCountsTheAttempt() {
        PushOutboxEntry entry = queued();
        whenNotificationExists(entry, device());
        when(webPush.send(any(String.class), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("service injoignable"));
        when(outbox.findById(any())).thenReturn(Optional.of(entry));

        dispatcher.dispatchPending();

        assertThat(entry.getAttempts()).isEqualTo(1);
        verify(outbox, never()).delete(entry);
    }

    /** Un service qui refuse durablement ne changera pas d'avis parce qu'on insiste. */
    @Test
    void anEntryIsAbandonedOnceTheAttemptsRunOut() {
        PushOutboxEntry entry = queued();
        for (int attempt = 0; attempt < properties.getMaxAttempts() - 1; attempt++) {
            entry.incrementAttempts();
        }
        whenNotificationExists(entry, device());
        when(webPush.send(any(String.class), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("service injoignable"));
        when(outbox.findById(any())).thenReturn(Optional.of(entry));

        dispatcher.dispatchPending();

        verify(outbox).delete(entry);
    }

    /** La notification a pu etre supprimee entre l'ecriture et l'envoi : il n'y a plus rien a pousser. */
    @Test
    void aVanishedNotificationJustDropsTheEntry() {
        PushOutboxEntry entry = queued();
        when(outbox.findByOrderByCreatedAtAsc(any())).thenReturn(List.of(entry));
        when(notifications.findById(entry.getNotificationId())).thenReturn(Optional.empty());

        dispatcher.dispatchPending();

        verify(outbox).delete(entry);
        verify(webPush, never()).send(any(String.class), any(), any(), any(), any(), any(), any());
    }

    /**
     * Deux notifications ne doivent jamais porter la meme etiquette.
     *
     * <p>Le navigateur remplace une notification par une autre de meme {@code tag}, sans
     * bruit : la premiere disparait, et sur un ecran verrouille personne ne voit qu'il en
     * manquait une. Le regroupement par type avait ete choisi pour eviter d'empiler dix
     * bannieres ; il faisait perdre neuf informations sur dix, et rendait tout envoi de
     * test invisible apres le premier - ce qui est exactement comme ca qu'on s'en est
     * apercu.</p>
     */
    @Test
    void twoNotificationsNeverShareATagAndCannotSilentlyReplaceEachOther() {
        List<String> payloads = new java.util.ArrayList<>();
        PushSubscription device = device();
        when(webPush.send(any(String.class), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    payloads.add(invocation.getArgument(0));
                    return WebPush.SubscriptionState.ACTIVE;
                });

        for (int round = 0; round < 2; round++) {
            PushOutboxEntry entry = queued();
            whenNotificationExists(entry, device);
            dispatcher.dispatchPending();
        }

        assertThat(payloads).hasSize(2);
        assertThat(tagOf(payloads.get(0))).isNotEqualTo(tagOf(payloads.get(1)));
    }

    private static String tagOf(String payload) {
        try {
            return new ObjectMapper().readTree(payload).path("tag").asText();
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    private void whenNotificationExists(PushOutboxEntry entry, PushSubscription device) {
        AppUser recipient = device.getUser();
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipient(recipient);
        notification.setType(NotificationType.CHALLENGE_SUBMISSION_VALIDATED);
        notification.setTitle("Défi validé");
        notification.setBody("Tu as validé un défi.");
        notification.setPayload(Map.of());

        when(outbox.findByOrderByCreatedAtAsc(any())).thenReturn(List.of(entry));
        when(notifications.findById(entry.getNotificationId())).thenReturn(Optional.of(notification));
        when(subscriptions.findByUserId(recipient.getId())).thenReturn(List.of(device));
    }

    private static PushOutboxEntry queued() {
        return new PushOutboxEntry(UUID.randomUUID());
    }

    private static PushSubscription device() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("sub");
        user.setEmail("joueur@example.test");
        return new PushSubscription(user, "https://push.example/abc", "p256", "auth", "UA");
    }
}
