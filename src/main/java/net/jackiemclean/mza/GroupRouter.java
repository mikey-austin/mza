package net.jackiemclean.mza;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GroupRouter {

    private static final Logger LOG = LoggerFactory.getLogger(GroupRouter.class);

    @Autowired(required = false)
    private Optional<MqttClient> mqttClient;

    @Value("${mqtt.topic.base.group:mza/group/}")
    String topicBase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void publishGroupToMqtt(GroupState groupState) {
        if (mqttClient == null || mqttClient.isEmpty()) {
            return;
        }

        MqttClient client = mqttClient.get();
        try {
            String topic = topicBase + groupState.getName();

            // Publish zones as JSON array with retained flag
            String zonesJson = objectMapper.writeValueAsString(groupState.getZones());
            MqttMessage zonesMsg = new MqttMessage(zonesJson.getBytes());
            zonesMsg.setRetained(true);
            client.publish(topic + "/zones", zonesMsg);

            // Publish display name
            if (groupState.getDisplayName() != null) {
                MqttMessage displayNameMsg = new MqttMessage(groupState.getDisplayName().getBytes());
                displayNameMsg.setRetained(true);
                client.publish(topic + "/displayName", displayNameMsg);
            }

            // Publish description
            if (groupState.getDescription() != null) {
                MqttMessage descMsg = new MqttMessage(groupState.getDescription().getBytes());
                descMsg.setRetained(true);
                client.publish(topic + "/description", descMsg);
            }

            // Publish timestamps
            MqttMessage createdMsg = new MqttMessage(groupState.getCreatedAt().toString().getBytes());
            createdMsg.setRetained(true);
            client.publish(topic + "/created_at", createdMsg);

            MqttMessage updatedMsg = new MqttMessage(groupState.getUpdatedAt().toString().getBytes());
            updatedMsg.setRetained(true);
            client.publish(topic + "/updated_at", updatedMsg);

            LOG.debug("Published group {} to MQTT", groupState.getName());
        } catch (Exception e) {
            LOG.error("Failed to publish group to MQTT", e);
        }
    }

    public void publishGroupDeletion(String groupName) {
        if (mqttClient == null || mqttClient.isEmpty()) {
            return;
        }

        MqttClient client = mqttClient.get();
        try {
            String topic = topicBase + groupName;

            // Publish empty retained messages to clear state
            MqttMessage emptyMsg = new MqttMessage(new byte[0]);
            emptyMsg.setRetained(true);

            client.publish(topic + "/zones", emptyMsg);
            client.publish(topic + "/displayName", emptyMsg);
            client.publish(topic + "/description", emptyMsg);
            client.publish(topic + "/created_at", emptyMsg);
            client.publish(topic + "/updated_at", emptyMsg);

            LOG.debug("Published group deletion for {} to MQTT", groupName);
        } catch (Exception e) {
            LOG.error("Failed to publish group deletion to MQTT", e);
        }
    }
}
