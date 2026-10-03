package com.example.demo.WebSocket.services;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DriverPresenceService {

    private final SimpMessagingTemplate messagingTemplate;
    private final DriverPresenceRegistry driverPresenceRegistry;

    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (driverPresenceRegistry.isDriverSession(sessionId)) {
            broadcastCount();
        }
    }

    public int getOnlineDriverCount() {
        return driverPresenceRegistry.getOnlineDriverCount();
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        if (driverPresenceRegistry.disconnect(event.getSessionId())) {
            broadcastCount();
        }
    }

    private void broadcastCount() {
        messagingTemplate.convertAndSend("/topic/driversOnline", getOnlineDriverCount());
    }
}
