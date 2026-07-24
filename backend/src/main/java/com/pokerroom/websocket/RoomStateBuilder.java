package com.pokerroom.websocket;

import com.pokerroom.model.Room;
import com.pokerroom.store.RoomStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RoomStateBuilder {

    public static final List<Object> CARD_VALUES =
            List.of(0, 1, 2, 3, 5, 8, 13, 21, 34, "?", "coffee");

    private final RoomStore roomStore;
    private final RoomConnectionManager connectionManager;

    public RoomStateBuilder(RoomStore roomStore, RoomConnectionManager connectionManager) {
        this.roomStore = roomStore;
        this.connectionManager = connectionManager;
    }

    public Map<String, Object> build(String roomId) {
        Room room = roomStore.find(roomId)
                .orElseThrow(() -> new IllegalStateException("Room not found: " + roomId));

        List<Map<String, Object>> participants = new ArrayList<>();
        for (String name : connectionManager.participantNames(roomId)) {
            boolean hasVoted = room.getVotes().containsKey(name);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("has_voted", hasVoted);
            entry.put("value", (room.isRevealed() && hasVoted) ? room.getVotes().get(name) : null);
            participants.add(entry);
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("type", "state");
        state.put("room_id", room.getId());
        state.put("revealed", room.isRevealed());
        state.put("participants", participants);
        state.put("card_values", CARD_VALUES);
        return state;
    }
}
