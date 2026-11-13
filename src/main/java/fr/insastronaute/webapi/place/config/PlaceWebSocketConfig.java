package fr.insastronaute.webapi.place.config;

import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class PlaceWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppSecurityProperties securityProperties;
    private final PlaceStompInboundInterceptor inboundInterceptor;

    public PlaceWebSocketConfig(
            AppSecurityProperties securityProperties,
            PlaceStompInboundInterceptor inboundInterceptor
    ) {
        this.securityProperties = securityProperties;
        this.inboundInterceptor = inboundInterceptor;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(inboundInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/place")
                .setAllowedOriginPatterns(securityProperties.getAllowedOrigins().toArray(String[]::new));
    }
}
