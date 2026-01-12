package net.jackiemclean.mza.jsonrpc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for Snapcast JSON-RPC protocol.
 * Registers the /jsonrpc endpoint for WebSocket connections.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private JsonRpcWebSocketHandler jsonRpcHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(jsonRpcHandler, "/jsonrpc")
                .setAllowedOrigins("*");
    }
}
