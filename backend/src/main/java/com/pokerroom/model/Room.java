package com.pokerroom.model;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A planning poker room. Lives entirely in memory - votes are keyed by
 * participant name so a brief disconnect/reconnect (e.g. a page refresh)
 * doesn't lose someone's vote for the current round.
 */
public class Room {

    private final String id;
    private final Instant createdAt = Instant.now();
    private volatile boolean revealed = false;
    // ConcurrentHashMap is a thread-safe version of a Java Map.
    // It lets multiple threads safely read and update the map at the same time, without needing you to manually add synchronized blocks.
    // participant name → chosen card value
    private final Map<String, String> votes = new ConcurrentHashMap<>();

    public Room(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRevealed() {
        return revealed;
    }

    public void setRevealed(boolean revealed) {
        this.revealed = revealed;
    }

    public Map<String, String> getVotes() {
        return votes;
    }
}
