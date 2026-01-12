package net.jackiemclean.mza;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JsonRpcWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String wsUrl;

    @BeforeEach
    void setUp() {
        wsUrl = "ws://localhost:" + port + "/jsonrpc";
    }

    @Test
    void testServerGetRPCVersion() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"method\":\"Server.GetRPCVersion\",\"id\":1}";
        JsonNode response = sendAndReceive(request);

        assertEquals("2.0", response.get("jsonrpc").asText());
        assertEquals(1, response.get("id").asInt());
        assertNotNull(response.get("result"));
        assertEquals(2, response.get("result").get("major").asInt());
        assertEquals(0, response.get("result").get("minor").asInt());
        assertEquals(0, response.get("result").get("patch").asInt());
    }

    @Test
    void testServerGetStatus() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"method\":\"Server.GetStatus\",\"id\":2}";
        JsonNode response = sendAndReceive(request);

        assertEquals("2.0", response.get("jsonrpc").asText());
        assertEquals(2, response.get("id").asInt());
        assertNotNull(response.get("result"));

        JsonNode server = response.get("result").get("server");
        assertNotNull(server);
        assertNotNull(server.get("groups"));
        assertNotNull(server.get("streams"));
        assertTrue(server.get("streams").isArray());
    }

    @Test
    void testClientGetStatus() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"method\":\"Client.GetStatus\",\"params\":{\"id\":\"test_zone\"},\"id\":3}";
        JsonNode response = sendAndReceive(request);

        assertEquals("2.0", response.get("jsonrpc").asText());
        assertEquals(3, response.get("id").asInt());
        assertNotNull(response.get("result"));

        JsonNode client = response.get("result").get("client");
        assertNotNull(client);
        assertEquals("test_zone", client.get("id").asText());
        assertTrue(client.get("connected").asBoolean());
    }

    @Test
    void testClientGetStatus_NotFound() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"method\":\"Client.GetStatus\",\"params\":{\"id\":\"nonexistent\"},\"id\":4}";
        JsonNode response = sendAndReceive(request);

        assertEquals("2.0", response.get("jsonrpc").asText());
        assertEquals(4, response.get("id").asInt());
        assertNotNull(response.get("error"));
        assertEquals(-32602, response.get("error").get("code").asInt());
    }

    @Test
    void testClientSetVolume() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"method\":\"Client.SetVolume\",\"params\":{\"id\":\"test_zone\",\"volume\":{\"percent\":75,\"muted\":false}},\"id\":5}";
        JsonNode response = sendAndReceive(request);

        assertEquals("2.0", response.get("jsonrpc").asText());
        assertEquals(5, response.get("id").asInt());
        assertNull(response.get("error"), "Unexpected error: " + response.get("error"));
        assertNotNull(response.get("result"), "Response: " + response);

        JsonNode volume = response.get("result").get("volume");
        assertNotNull(volume, "Volume not in result: " + response.get("result"));
        assertEquals(75, volume.get("percent").asInt());
        assertFalse(volume.get("muted").asBoolean());
    }

    @Test
    void testMethodNotFound() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"method\":\"Unknown.Method\",\"id\":6}";
        JsonNode response = sendAndReceive(request);

        assertEquals("2.0", response.get("jsonrpc").asText());
        assertEquals(6, response.get("id").asInt());
        assertNotNull(response.get("error"));
        assertEquals(-32601, response.get("error").get("code").asInt());
    }

    @Test
    void testInvalidRequest() throws Exception {
        String request = "{\"jsonrpc\":\"1.0\",\"method\":\"Server.GetStatus\",\"id\":7}";
        JsonNode response = sendAndReceive(request);

        assertEquals(7, response.get("id").asInt());
        assertNotNull(response.get("error"));
        assertEquals(-32600, response.get("error").get("code").asInt());
    }

    @Test
    void testParseError() throws Exception {
        String request = "not valid json";
        JsonNode response = sendAndReceive(request);

        assertNotNull(response.get("error"));
        assertEquals(-32700, response.get("error").get("code").asInt());
    }

    @Test
    void testMissingParams() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"method\":\"Client.GetStatus\",\"id\":8}";
        JsonNode response = sendAndReceive(request);

        assertEquals(8, response.get("id").asInt());
        assertNotNull(response.get("error"));
        assertEquals(-32602, response.get("error").get("code").asInt());
    }

    @Test
    void testStreamMethods_NotSupported() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"method\":\"Stream.AddStream\",\"params\":{\"streamUri\":\"pipe:///tmp/test\"},\"id\":9}";
        JsonNode response = sendAndReceive(request);

        assertEquals(9, response.get("id").asInt());
        assertNotNull(response.get("error"));
        // Should return an error since dynamic streams are not supported
    }

    private JsonNode sendAndReceive(String request) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> responseHolder = new AtomicReference<>();

        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                String payload = message.getPayload().trim();
                try {
                    // Check if this is a response or notification
                    JsonNode node = objectMapper.readTree(payload);
                    // A response has either "result" or "error" field
                    // A notification has "method" field and no "id"
                    if (node.has("result") || node.has("error")) {
                        // This is a response
                        responseHolder.set(payload);
                        latch.countDown();
                    }
                    // Ignore notifications (they have "method" field)
                } catch (Exception e) {
                    responseHolder.set(payload);
                    latch.countDown();
                }
            }
        }, wsUrl).get(5, TimeUnit.SECONDS);

        try {
            session.sendMessage(new TextMessage(request));
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Timeout waiting for response");
            return objectMapper.readTree(responseHolder.get());
        } finally {
            session.close();
        }
    }
}
