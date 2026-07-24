package com.pokerroom.dto;

import com.pokerroom.model.Room;

import java.time.Instant;

public record RoomResponse(String id, Instant createdAt) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getCreatedAt());
    }
}
