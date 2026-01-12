package net.jackiemclean.mza.jsonrpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jackiemclean.mza.snapcast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages WebSocket sessions and broadcasts notifications to all connected
 * clients (both WebSocket and TCP).
 */
@Service
public class SnapcastNotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(SnapcastNotificationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Autowired(required = false)
    @Lazy
    private SnapcastTcpServer tcpServer;

    public void addSession(WebSocketSession session) {
        sessions.add(session);
        LOG.debug("WebSocket session added: {}, total sessions: {}", session.getId(), sessions.size());
    }

    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
        LOG.debug("WebSocket session removed: {}, total sessions: {}", session.getId(), sessions.size());
    }

    public void broadcast(JsonRpcNotification notification) {
        String message;
        try {
            message = objectMapper.writeValueAsString(notification) + "\n";
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize notification", e);
            return;
        }

        // Broadcast to WebSocket clients
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    LOG.error("Failed to send notification to WebSocket session {}", session.getId(), e);
                }
            }
        }

        // Broadcast to TCP clients
        if (tcpServer != null) {
            tcpServer.broadcast(notification);
        }
    }

    public void broadcastExcept(WebSocketSession sender, JsonRpcNotification notification) {
        String message;
        try {
            message = objectMapper.writeValueAsString(notification) + "\n";
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize notification", e);
            return;
        }

        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen() && !session.getId().equals(sender.getId())) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    LOG.error("Failed to send notification to session {}", session.getId(), e);
                }
            }
        }
    }

    // ===== Client Notifications =====

    public void broadcastClientConnect(SnapClient client) {
        Map<String, Object> params = new HashMap<>();
        params.put("client", client);
        params.put("id", client.getId());
        broadcast(new JsonRpcNotification("Client.OnConnect", params));
    }

    public void broadcastClientDisconnect(SnapClient client) {
        Map<String, Object> params = new HashMap<>();
        params.put("client", client);
        params.put("id", client.getId());
        broadcast(new JsonRpcNotification("Client.OnDisconnect", params));
    }

    public void broadcastClientVolumeChanged(String clientId, SnapVolume volume) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", clientId);
        params.put("volume", volume);
        broadcast(new JsonRpcNotification("Client.OnVolumeChanged", params));
    }

    public void broadcastClientLatencyChanged(String clientId, int latency) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", clientId);
        params.put("latency", latency);
        broadcast(new JsonRpcNotification("Client.OnLatencyChanged", params));
    }

    public void broadcastClientNameChanged(String clientId, String name) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", clientId);
        params.put("name", name);
        broadcast(new JsonRpcNotification("Client.OnNameChanged", params));
    }

    // ===== Group Notifications =====

    public void broadcastGroupMute(String groupId, boolean mute) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", groupId);
        params.put("mute", mute);
        broadcast(new JsonRpcNotification("Group.OnMute", params));
    }

    public void broadcastGroupStreamChanged(String groupId, String streamId) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", groupId);
        params.put("stream_id", streamId);
        broadcast(new JsonRpcNotification("Group.OnStreamChanged", params));
    }

    public void broadcastGroupNameChanged(String groupId, String name) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", groupId);
        params.put("name", name);
        broadcast(new JsonRpcNotification("Group.OnNameChanged", params));
    }

    // ===== Stream Notifications =====

    public void broadcastStreamUpdate(String streamId, SnapStream stream) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", streamId);
        params.put("stream", stream);
        broadcast(new JsonRpcNotification("Stream.OnUpdate", params));
    }

    public void broadcastStreamProperties(String streamId, Map<String, Object> metadata) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", streamId);
        if (metadata != null) {
            params.put("metadata", metadata);
        }
        broadcast(new JsonRpcNotification("Stream.OnProperties", params));
    }

    // ===== Server Notifications =====

    public void broadcastServerUpdate(SnapServer server) {
        Map<String, Object> params = new HashMap<>();
        params.put("server", server);
        broadcast(new JsonRpcNotification("Server.OnUpdate", params));
    }
}
