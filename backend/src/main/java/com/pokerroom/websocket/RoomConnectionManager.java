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

    /**
     * Adds a person's WebSocket connection to the specified room.
     *
     * <p>The {@code rooms} map stores room IDs and their currently active
     * connections. For example:</p>
     *
     * <pre>
     * "ABC123" → {
     *     Connection(socket1, "Octavian"),
     *     Connection(socket2, "Sam")
     * }
     * </pre>
     * <p>If the room does not yet have a connection set, this method creates
     * a new thread-safe set and stores it before adding the connection.</p>
     * @param roomId the ID of the room being joined
     * @param socket the participant's WebSocket connection
     * @param name the participant's display name
     */
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

    /**
     * Sends the given JSON message to every currently open WebSocket connection
     * in the specified room.
     *
     * <p>If the room has no active connections, this method does nothing.
     * Closed sockets are skipped. If a send fails because a socket is no longer
     * usable, the error is ignored; the connection will be removed when its
     * close event is handled.</p>
     *
     * @param roomId the ID of the room whose connected participants receive the message
     * @param json the JSON payload to send
     */
    public void broadcast(String roomId, String json) {
        // getOrDefault - “Get the connections for this roomId; if none exist, use an empty set instead.”
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
