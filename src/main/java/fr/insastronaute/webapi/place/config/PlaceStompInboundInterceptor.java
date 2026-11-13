package fr.insastronaute.webapi.place.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Le portier de <strong>tout</strong> le courtier STOMP, malgre son nom et son paquet.
 *
 * <p>{@code configureClientInboundChannel} ne se limite pas au point d'entree declare
 * par la meme classe : il vaut pour toutes les connexions, celle de la fresque comme
 * celles des combats et de la cloche. Toute nouvelle file doit donc etre nommee ici,
 * sans quoi le SUBSCRIBE est refuse -- la poignee de main reussit, le serveur renvoie
 * une ERROR, et le client retente en boucle. C'est ce qui est arrive a
 * {@code /user/queue/notifications}.</p>
 *
 * <p>La liste est deliberement une liste d'autorisation : une destination oubliee se
 * voit tout de suite, alors qu'une liste d'interdiction laisse passer ce que personne
 * n'a pense a interdire.</p>
 */
@Component
public class PlaceStompInboundInterceptor implements ChannelInterceptor {

    // La fresque des departements n'a pas de topic a elle : c'est une lecture de la
    // fresque libre, et chaque pixel de celle-ci porte la couleur de son departement.
    static final String CANVAS_UPDATES_DESTINATION = "/topic/canvas-updates";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        String dest = accessor.getDestination();
        boolean isStsDestination = dest != null && (dest.startsWith("/app/sts/") || dest.startsWith("/sts/") || dest.startsWith("/user/queue/sts/") || dest.startsWith("/queue/sts/"));
        // La cloche et les equipes : le serveur y pousse, le client n'y ecrit jamais.
        // Elles restent donc hors de l'autorisation de SEND, juste en dessous.
        boolean isAnnouncementDestination = dest != null && (
                dest.startsWith("/user/queue/notifications") || dest.startsWith("/queue/notifications")
                || dest.startsWith("/user/queue/challenge-teams") || dest.startsWith("/queue/challenge-teams"));

        if (command == StompCommand.SEND && !isStsDestination) {
            throw new AccessDeniedException("Les messages STOMP clients sont interdits.");
        }

        requireAuthenticated(accessor);

        if (command == StompCommand.SUBSCRIBE
                && !CANVAS_UPDATES_DESTINATION.equals(dest)
                && !isStsDestination
                && !isAnnouncementDestination) {
            throw new AccessDeniedException("Destination STOMP interdite.");
        }

        if (command == StompCommand.CONNECT
                || command == StompCommand.STOMP
                || command == StompCommand.SUBSCRIBE
                || command == StompCommand.UNSUBSCRIBE
                || command == StompCommand.ACK
                || command == StompCommand.NACK
                || command == StompCommand.DISCONNECT
                || (command == StompCommand.SEND && isStsDestination)) {
            return message;
        }

        throw new AccessDeniedException("Commande STOMP interdite.");
    }

    private static void requireAuthenticated(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new AccessDeniedException("Authentification STOMP requise.");
        }
    }
}
