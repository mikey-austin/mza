package net.jackiemclean.mza;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupRouterTest {

    @Mock
    private MqttClient mqttClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GroupRouter groupRouter;

    private GroupState testGroup;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(groupRouter, "topicBase", "mza/group/");

        testGroup = new GroupState();
        testGroup.setName("test_group");
        testGroup.setDisplayName("Test Group");
        testGroup.setZones(Set.of("zone1", "zone2"));
        testGroup.setDescription("Test description");
        testGroup.setCreatedAt(Instant.parse("2026-01-11T10:00:00Z"));
        testGroup.setUpdatedAt(Instant.parse("2026-01-11T10:30:00Z"));
    }

    @Test
    void testPublishGroupToMqtt_Success() throws Exception {
        // Act
        groupRouter.publishGroupToMqtt(testGroup);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);

        verify(mqttClient, times(5)).publish(topicCaptor.capture(), messageCaptor.capture());

        var topics = topicCaptor.getAllValues();
        var messages = messageCaptor.getAllValues();

        // Verify zones topic
        assert topics.contains("mza/group/test_group/zones");
        // Verify all messages are retained
        for (MqttMessage msg : messages) {
            assert msg.isRetained();
        }
    }

    @Test
    void testPublishGroupToMqtt_WithNullDescription() throws Exception {
        // Arrange
        testGroup.setDescription(null);

        // Act
        groupRouter.publishGroupToMqtt(testGroup);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttClient, times(4)).publish(topicCaptor.capture(), any(MqttMessage.class));

        var topics = topicCaptor.getAllValues();
        // Should not publish description topic
        assert !topics.contains("mza/group/test_group/description");
    }

    @Test
    void testPublishGroupToMqtt_HandlesException() throws Exception {
        // Arrange
        doThrow(new RuntimeException("MQTT error"))
                .when(mqttClient)
                .publish(anyString(), any(MqttMessage.class));

        // Act - should not throw exception
        groupRouter.publishGroupToMqtt(testGroup);

        // Assert - verify it attempted to publish
        verify(mqttClient, atLeastOnce()).publish(anyString(), any(MqttMessage.class));
    }

    @Test
    void testPublishGroupDeletion_Success() throws Exception {
        // Act
        groupRouter.publishGroupDeletion("test_group");

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);

        verify(mqttClient, times(5)).publish(topicCaptor.capture(), messageCaptor.capture());

        var topics = topicCaptor.getAllValues();
        var messages = messageCaptor.getAllValues();

        // Verify all topics
        assert topics.contains("mza/group/test_group/zones");
        assert topics.contains("mza/group/test_group/displayName");
        assert topics.contains("mza/group/test_group/description");
        assert topics.contains("mza/group/test_group/created_at");
        assert topics.contains("mza/group/test_group/updated_at");

        // Verify all messages are empty and retained
        for (MqttMessage msg : messages) {
            assert msg.getPayload().length == 0;
            assert msg.isRetained();
        }
    }

    @Test
    void testPublishGroupDeletion_HandlesException() throws Exception {
        // Arrange
        doThrow(new RuntimeException("MQTT error"))
                .when(mqttClient)
                .publish(anyString(), any(MqttMessage.class));

        // Act - should not throw exception
        groupRouter.publishGroupDeletion("test_group");

        // Assert - verify it attempted to publish
        verify(mqttClient, atLeastOnce()).publish(anyString(), any(MqttMessage.class));
    }
}
