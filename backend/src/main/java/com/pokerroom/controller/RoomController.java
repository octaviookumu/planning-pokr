package com.pokerroom.controller;

import com.pokerroom.dto.RoomResponse;
import com.pokerroom.model.Room;
import com.pokerroom.store.RoomStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomStore roomStore;

    public RoomController(RoomStore roomStore) {
        this.roomStore = roomStore;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> create() {
        Room room = roomStore.createRoom();
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.from(room));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> get(@PathVariable String id) {
        return roomStore.find(id)
                .map(room -> ResponseEntity.ok(RoomResponse.from(room)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
