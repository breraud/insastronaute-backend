package fr.insastronaute.webapi.place.config;

import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PlaceStompInboundInterceptorTest {

    private final PlaceStompInboundInterceptor interceptor = new PlaceStompInboundInterceptor();
    private final MessageChannel channel = mock(MessageChannel.class);
    private final Principal player = () -> "player";

    @Test
    void allowsAuthenticatedSubscriptionToCanvasUpdates() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/topic/canvas-updates", player);

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void rejectsAnonymousConnection() {
        Message<byte[]> message = frame(StompCommand.CONNECT, null, null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Authentification STOMP requise.");
    }

    @Test
    void rejectsClientSendToServerTopic() {
        Message<byte[]> message = frame(StompCommand.SEND, "/topic/canvas-updates", player);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Les messages STOMP clients sont interdits.");
    }

    /**
     * La cloche et les equipes passent aussi par ce portier.
     *
     * <p>Ce n'etait pas le cas : la poignee de main reussissait, le SUBSCRIBE etait
     * refuse, le serveur renvoyait une ERROR et chaque onglet retentait en boucle. Ce
     * portier vaut pour tout le courtier, pas pour la seule fresque, et une file
     * oubliee ici ne se voit nulle part ailleurs.</p>
     */
    @Test
    void allowsAuthenticatedSubscriptionToTheBellAndTheTeams() {
        for (String destination : new String[]{
                "/user/queue/notifications", "/user/queue/challenge-teams"}) {
            Message<byte[]> message = frame(StompCommand.SUBSCRIBE, destination, player);

            assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        }
    }

    /** Le serveur y pousse, le client n'y ecrit jamais. */
    @Test
    void rejectsClientSendToTheBell() {
        Message<byte[]> message = frame(StompCommand.SEND, "/user/queue/notifications", player);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Les messages STOMP clients sont interdits.");
    }

    @Test
    void rejectsSubscriptionToUnknownDestination() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/topic/private", player);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Destination STOMP interdite.");
    }

    private Message<byte[]> frame(StompCommand command, String destination, Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (principal != null) {
            accessor.setUser(principal);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
