package fr.insastronaute.webapi.config;

import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import fr.insastronaute.webapi.place.config.PlaceStompInboundInterceptor;
import fr.insastronaute.webapi.place.config.PlaceWebSocketConfig;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketBrokerConfigurationTest {

    @Test
    void centralBrokerSupportsTopicsAndUserQueues() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        new WebSocketConfig(mock(AppSecurityProperties.class)).configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic", "/queue");
        verify(registry).setApplicationDestinationPrefixes("/app");
    }

    /**
     * Les deux points d'entrée, et la même liste d'origines pour les deux.
     *
     * <p>Celui des notifications a été ajouté après coup : l'oublier ici ferait passer
     * un point d'entrée ouvert à toutes les origines sans que rien ne le dise.</p>
     */
    @Test
    void bothEndpointsUseTheConfiguredOrigins() {
        AppSecurityProperties properties = mock(AppSecurityProperties.class);
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration sts = mock(StompWebSocketEndpointRegistration.class);
        StompWebSocketEndpointRegistration notifications = mock(StompWebSocketEndpointRegistration.class);
        when(properties.getAllowedOrigins()).thenReturn(List.of("https://app.example.com"));
        when(registry.addEndpoint("/ws/sts")).thenReturn(sts);
        when(registry.addEndpoint("/ws/notifications")).thenReturn(notifications);

        new WebSocketConfig(properties).registerStompEndpoints(registry);

        verify(sts).setAllowedOriginPatterns("https://app.example.com");
        verify(notifications).setAllowedOriginPatterns("https://app.example.com");
    }

    @Test
    void placeConfigurerDoesNotReplaceTheSharedBroker() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        PlaceWebSocketConfig config = new PlaceWebSocketConfig(
                mock(AppSecurityProperties.class),
                mock(PlaceStompInboundInterceptor.class));

        config.configureMessageBroker(registry);

        verify(registry, never()).enableSimpleBroker(org.mockito.ArgumentMatchers.<String[]>any());
    }
}
