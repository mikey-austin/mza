package net.jackiemclean.mza.jsonrpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Raw TCP socket server for Snapcast JSON-RPC protocol.
 * Snapcast clients (like Snapdroid) connect via TCP on port 1705 (default).
 * Messages are newline-delimited JSON (ndjson).
 */
@Component
public class SnapcastTcpServer {

    private static final Logger LOG = LoggerFactory.getLogger(SnapcastTcpServer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, TcpClientSession> sessions = new ConcurrentHashMap<>();

    @Value("${snapcast.tcp.port:1705}")
    private int tcpPort;

    @Value("${snapcast.tcp.enabled:true}")
    private boolean enabled;

    @Autowired
    private SnapcastMethodHandler methodHandler;

    @Autowired
    private SnapcastNotificationService notificationService;

    private ServerSocket serverSocket;
    private ExecutorService executorService;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            LOG.info("Snapcast TCP server is disabled");
            return;
        }

        executorService = Executors.newVirtualThreadPerTaskExecutor();
        executorService.submit(this::runServer);
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(tcpPort);
            LOG.info("Snapcast TCP server listening on port {}", tcpPort);

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    LOG.info("New TCP connection from {}", clientSocket.getRemoteSocketAddress());
                    executorService.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        LOG.error("Error accepting connection", e);
                    }
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to start TCP server on port {}", tcpPort, e);
        }
    }

    private void handleClient(Socket socket) {
        String sessionId = socket.getRemoteSocketAddress().toString();
        TcpClientSession session = new TcpClientSession(sessionId, socket);
        sessions.put(sessionId, session);

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            session.setWriter(writer);

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                LOG.debug("Received from {}: {}", sessionId, line);
                handleMessage(session, line);
            }
        } catch (IOException e) {
            LOG.debug("Client {} disconnected: {}", sessionId, e.getMessage());
        } finally {
            sessions.remove(sessionId);
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
            LOG.info("TCP connection closed: {}", sessionId);
        }
    }

    private void handleMessage(TcpClientSession session, String json) {
        try {
            json = json.trim();

            // Check if this is a batch request (starts with '[')
            if (json.startsWith("[")) {
                handleBatchRequest(session, json);
                return;
            }

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

    private void handleBatchRequest(TcpClientSession session, String json) {
        try {
            JsonRpcRequest[] requests = objectMapper.readValue(json, JsonRpcRequest[].class);

            for (JsonRpcRequest request : requests) {
                // Validate JSON-RPC version
                if (!"2.0".equals(request.getJsonrpc())) {
                    sendResponse(session, JsonRpcResponse.error(request.getId(), JsonRpcError.invalidRequest()));
                    continue;
                }

                JsonRpcResponse response = methodHandler.handleRequest(request);

                // Only send response if it has an id (not a notification)
                if (request.getId() != null) {
                    sendResponse(session, response);
                }
            }
        } catch (JsonProcessingException e) {
            LOG.error("Failed to parse batch request: {}", e.getMessage());
            sendResponse(session, JsonRpcResponse.error(null, JsonRpcError.parseError()));
        }
    }

    private void sendResponse(TcpClientSession session, JsonRpcResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            session.send(json);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize response", e);
        }
    }

    public void broadcast(JsonRpcNotification notification) {
        try {
            String json = objectMapper.writeValueAsString(notification);
            for (TcpClientSession session : sessions.values()) {
                session.send(json);
            }
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize notification", e);
        }
    }

    public Set<String> getConnectedClients() {
        return sessions.keySet();
    }

    /**
     * Represents a connected TCP client session.
     */
    public static class TcpClientSession {
        private final String id;
        private final Socket socket;
        private PrintWriter writer;

        public TcpClientSession(String id, Socket socket) {
            this.id = id;
            this.socket = socket;
        }

        public void setWriter(PrintWriter writer) {
            this.writer = writer;
        }

        public synchronized void send(String message) {
            if (writer != null) {
                writer.println(message);
                writer.flush();
            }
        }

        public String getId() {
            return id;
        }

        public Socket getSocket() {
            return socket;
        }
    }
}
