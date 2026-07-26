package com.pokerroom.websocket;

import com.pokerroom.model.Room;
import com.pokerroom.store.RoomStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private final RoomConnectionManager connectionManager;
    private final RoomStateBuilder stateBuilder;
    private final RoomStore roomStore;
    private final ObjectMapper objectMapper;

    /**
        Phase 2: While creating RoomWebSocketHandler, Spring injects
    */
    public RoomWebSocketHandler(
            RoomConnectionManager connectionManager,
            RoomStateBuilder stateBuilder,
            RoomStore roomStore,
            ObjectMapper objectMapper
    ) {
        this.connectionManager = connectionManager;
        this.stateBuilder = stateBuilder;
        this.roomStore = roomStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        String roomId = roomIdOf(session);
        String name = nameOf(session);
        connectionManager.add(roomId, session, name);
        broadcastState(roomId); // broadcast the current state to everybody in the room
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        String roomId = roomIdOf(session);
        String name = nameOf(session);

        Room room = roomStore.find(roomId).orElse(null);
        if (room != null) {
            try {
                Map<?, ?> payload = objectMapper.readValue(message.getPayload(), Map.class);
                String action = String.valueOf(payload.get("action"));

                switch (action) {
                    case "vote" -> room.getVotes().put(name, String.valueOf(payload.get("value")));
                    case "reveal" -> room.setRevealed(true);
                    case "reset" -> {
                        room.getVotes().clear();
                        room.setRevealed(false);
                    }
                    default -> {
                        // unrecognized action - ignore, still re-broadcast current state below
                    }
                }
            } catch (Exception ignored) {
                // Malformed message from a client - ignore and keep the connection alive.
            }
        }

        broadcastState(roomId);
    }

    /*
     * RoomConnectionManager removes the socket connection, not the participant's vote.
     * Votes belong to names in Room.votes,
     * so a participant who reconnects using the same name can retain their vote for the current round
     */
    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        String roomId = roomIdOf(session);
        connectionManager.remove(roomId, session);
        broadcastState(roomId);
    }

    private void broadcastState(String roomId) {
        try {
            Map<String, Object> state = stateBuilder.build(roomId);
            connectionManager.broadcast(roomId, objectMapper.writeValueAsString(state));
        } catch (Exception ignored) {
            // Room may have been cleaned up mid-flight; skip this broadcast.
        }
    }

    private String roomIdOf(WebSocketSession session) {
        return (String) session.getAttributes().get("roomId");
    }

    private String nameOf(WebSocketSession session) {
        return (String) session.getAttributes().get("name");
    }
}
