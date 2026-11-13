package fr.insastronaute.webapi.config;

import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppSecurityProperties securityProperties;

    public WebSocketConfig(AppSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/sts").setAllowedOriginPatterns(
                securityProperties.getAllowedOrigins().toArray(String[]::new));
        // La cloche a son propre point d'entree, sur le meme courtier : une
        // notification n'a rien a voir avec un combat, et les melanger rendrait
        // illisible la raison pour laquelle l'application ouvre une connexion.
        registry.addEndpoint("/ws/notifications").setAllowedOriginPatterns(
                securityProperties.getAllowedOrigins().toArray(String[]::new));
    }
}
