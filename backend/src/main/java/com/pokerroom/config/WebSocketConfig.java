package com.pokerroom.config;

import com.pokerroom.store.RoomStore;
import com.pokerroom.websocket.RoomHandshakeInterceptor;
import com.pokerroom.websocket.RoomWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomWebSocketHandler roomWebSocketHandler;
    private final RoomStore roomStore;

    public WebSocketConfig(RoomWebSocketHandler roomWebSocketHandler, RoomStore roomStore) {
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.roomStore = roomStore;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(roomWebSocketHandler, "/ws/rooms/*")
                .addInterceptors(new RoomHandshakeInterceptor(roomStore))
                .setAllowedOrigins("http://localhost:4200");
    }
}
