package com.pokerroom.websocket;

import com.pokerroom.store.RoomStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

public class RoomHandshakeInterceptor implements HandshakeInterceptor {

    private final RoomStore roomStore;

    public RoomHandshakeInterceptor(RoomStore roomStore) {
        this.roomStore = roomStore;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        // For this connection URL: ws://localhost:8080/ws/rooms/ABC123?name=Octavian
        // path becomes: /ws/rooms/ABC123
        // This extracts everything after the final /
        // path.lastIndexOf('/') finds the position of the last slash.
        // + 1 moves to the first character after that slash.
        // substring(...) returns the text from there until the end
        String path = request.getURI().getPath();
        String roomId = path.substring(path.lastIndexOf('/') + 1);

        if (roomStore.find(roomId).isEmpty()) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }

        // It gets the name value from the URL query parameters.
        String name = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("name");

        if (name == null || name.isBlank()) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        attributes.put("roomId", roomId);
        attributes.put("name", name.trim());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }
}
