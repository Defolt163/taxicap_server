package com.example.demo.WebSocket.services;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

@Component
public class DriverPresenceRegistry {

    private final ConcurrentMap<String, String> driverSessions = new ConcurrentHashMap<>();

    public void connect(String sessionId, String userId, Integer role) {
        if (sessionId == null || userId == null || role == null || role != 1) return;
        driverSessions.put(sessionId, userId);
    }

    public boolean isDriverSession(String sessionId) {
        return sessionId != null && driverSessions.containsKey(sessionId);
    }

    public int getOnlineDriverCount() {
        return new HashSet<>(driverSessions.values()).size();
    }

    public boolean disconnect(String sessionId) {
        String userId = driverSessions.remove(sessionId);
        return userId != null && !driverSessions.containsValue(userId);
    }
}
