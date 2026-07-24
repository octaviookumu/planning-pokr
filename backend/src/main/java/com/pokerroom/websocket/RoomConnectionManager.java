package com.pokerroom.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomConnectionManager {

    public record Connection(WebSocketSession socket, String name) {
    }

    private final Map<String, Set<Connection>> rooms = new ConcurrentHashMap<>();

    public void add(String roomId, WebSocketSession socket, String name) {
        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
                .add(new Connection(socket, name));
    }

    public void remove(String roomId, WebSocketSession socket) {
        Set<Connection> connections = rooms.get(roomId);
        if (connections != null) {
            connections.removeIf(c -> c.socket().getId().equals(socket.getId()));
        }
    }

    public List<String> participantNames(String roomId) {
        return rooms.getOrDefault(roomId, Set.of()).stream()
                .map(Connection::name)
                .distinct()
                .sorted()
                .toList();
    }

    public void broadcast(String roomId, String json) {
        Set<Connection> connections = rooms.getOrDefault(roomId, Set.of());
        TextMessage message = new TextMessage(json);
        for (Connection connection : connections) {
            try {
                if (connection.socket().isOpen()) {
                    connection.socket().sendMessage(message);
                }
            } catch (IOException ignored) {
                // A dead socket will be cleaned up on its own close event.
            }
        }
    }
}
