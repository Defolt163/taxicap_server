package com.example.demo.WebSocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.example.demo.WebSocket.services.DriverPresenceRegistry;
import com.example.demo.controllers.JwtValidator;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private JwtValidator jwtValidator;

    @Autowired
    private DriverPresenceRegistry driverPresenceRegistry;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    System.out.println("🔐 Processing CONNECT message");

                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    System.out.println("Authorization header: " + authHeader);

                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        System.out.println("Token received: " + token.substring(0, Math.min(token.length(), 50)) + "...");

                        try {
                            if (jwtValidator.isValid(token)) {
                                String id = jwtValidator.getUserId(token);
                                String email = jwtValidator.getEmail(token);
                                Object roleClaim = jwtValidator.validateToken(token).get("role");
                                Integer role = roleClaim instanceof Number ? ((Number) roleClaim).intValue() : null;

                                System.out.println("✅ User authenticated: " + email + " (id: " + id + ")");

                                // Создаем Principal
                                final String userIdFinal = id;
                                accessor.setUser(() -> userIdFinal);
                                accessor.setLeaveMutable(true);
                                driverPresenceRegistry.connect(accessor.getSessionId(), id, role);
                            } else {
                                System.out.println("❌ Invalid token");
                                throw new RuntimeException("Invalid JWT token");
                            }
                        } catch (Exception e) {
                            System.out.println("❌ Authentication error: " + e.getMessage());
                            e.printStackTrace();
                            throw new RuntimeException("Authentication failed: " + e.getMessage());
                        }
                    } else {
                        System.out.println("❌ Missing or invalid Authorization header");
                        throw new RuntimeException("Missing or invalid Authorization header");
                    }
                }
                return message;
            }
        });
    }
}