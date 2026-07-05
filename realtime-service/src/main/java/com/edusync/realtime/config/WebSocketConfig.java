package com.edusync.realtime.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WHY explicit allowed origins instead of "*": the audit flagged
 * `setAllowedOriginPatterns("*")` as a permissive CORS-equivalent wildcard on
 * a WebSocket endpoint (item 12 of the remediation plan). A wildcard origin
 * on a STOMP/SockJS endpoint means any website in the world can open a
 * browser-based WebSocket session against this service and, if a caller ever
 * attaches identity headers/cookies here in the future, ride the user's
 * session cross-origin. The fix mirrors api-gateway's own CORS lockdown
 * (see api-gateway/application.yml): a comma-separated allow-list, sourced
 * from an environment variable so each deployment (local dev, Render
 * preview, Render production) can declare its own real front-end origin(s)
 * without a code change, defaulting to the local dev origins used by this
 * project's own tooling when the env var is unset.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;

    public WebSocketConfig(
            @Value("${realtime.cors.allowed-origins:http://localhost:3000,http://localhost:5173}") String allowedOriginsCsv) {
        this.allowedOrigins = allowedOriginsCsv.split(",");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins).withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
