package net.jackiemclean.mza.jsonrpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket handler for Snapcast JSON-RPC protocol.
 * Handles newline-delimited JSON messages (ndjson) per Snapcast spec.
 */
@Component
public class JsonRpcWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JsonRpcWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SnapcastMethodHandler methodHandler;

    @Autowired
    private SnapcastNotificationService notificationService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        LOG.info("WebSocket connection established: {}", session.getId());
        notificationService.addSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        LOG.info("WebSocket connection closed: {}, status: {}", session.getId(), status);
        notificationService.removeSession(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload().trim();
        LOG.debug("Received message: {}", payload);

        if (payload.isEmpty()) {
            return;
        }

        // Handle batch requests (JSON array)
        if (payload.startsWith("[")) {
            handleBatchRequest(session, payload);
            return;
        }

        // Handle single request (possibly multiple lines for ndjson)
        String[] lines = payload.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                handleSingleRequest(session, line);
            }
        }
    }

    private void handleSingleRequest(WebSocketSession session, String json) {
        try {
            JsonRpcRequest request = objectMapper.readValue(json, JsonRpcRequest.class);

            // Validate JSON-RPC version
            if (!"2.0".equals(request.getJsonrpc())) {
                sendResponse(session, JsonRpcResponse.error(request.getId(), JsonRpcError.invalidRequest()));
                return;
            }

            JsonRpcResponse response = methodHandler.handleRequest(request);

            // Only send response if it has an id (not a notification)
            if (request.getId() != null) {
                sendResponse(session, response);
            }

        } catch (JsonProcessingException e) {
            LOG.error("Failed to parse JSON-RPC request: {}", e.getMessage());
            sendResponse(session, JsonRpcResponse.error(null, JsonRpcError.parseError()));
        }
    }

    private void handleBatchRequest(WebSocketSession session, String json) {
        try {
            JsonNode batchNode = objectMapper.readTree(json);
            if (!batchNode.isArray()) {
                sendResponse(session, JsonRpcResponse.error(null, JsonRpcError.invalidRequest()));
                return;
            }

            List<JsonRpcResponse> responses = new ArrayList<>();
            for (JsonNode node : batchNode) {
                try {
                    JsonRpcRequest request = objectMapper.treeToValue(node, JsonRpcRequest.class);
                    if (!"2.0".equals(request.getJsonrpc())) {
                        responses.add(JsonRpcResponse.error(request.getId(), JsonRpcError.invalidRequest()));
                        continue;
                    }
                    JsonRpcResponse response = methodHandler.handleRequest(request);
                    if (request.getId() != null) {
                        responses.add(response);
                    }
                } catch (JsonProcessingException e) {
                    responses.add(JsonRpcResponse.error(null, JsonRpcError.parseError()));
                }
            }

            // Send batch response
            if (!responses.isEmpty()) {
                sendBatchResponse(session, responses);
            }

        } catch (JsonProcessingException e) {
            LOG.error("Failed to parse batch request: {}", e.getMessage());
            sendResponse(session, JsonRpcResponse.error(null, JsonRpcError.parseError()));
        }
    }

    private void sendResponse(WebSocketSession session, JsonRpcResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response) + "\n";
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            LOG.error("Failed to send response to session {}", session.getId(), e);
        }
    }

    private void sendBatchResponse(WebSocketSession session, List<JsonRpcResponse> responses) {
        try {
            String json = objectMapper.writeValueAsString(responses) + "\n";
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            LOG.error("Failed to send batch response to session {}", session.getId(), e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        LOG.error("WebSocket transport error on session {}: {}", session.getId(), exception.getMessage());
        notificationService.removeSession(session);
    }
}
