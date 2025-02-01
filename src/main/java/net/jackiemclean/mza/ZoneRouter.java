package net.jackiemclean.mza;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ZoneRouter {

  private static final Logger LOG = LoggerFactory.getLogger(ZoneRouter.class);

  @Autowired ZoneStateRepository zoneStateRepository;
  @Autowired ZoneRepository zoneRepository;
  @Autowired SourceRepository sourceRepository;
  @Autowired AudioInterface audioInterface;
  @Autowired MqttClient mqttClient;

  @Value("${mqtt.topic.base}")
  String topicBase;

  public void syncZone(ZoneState zoneState) {
    var zone = zoneRepository.findByName(zoneState.getName());
    var source = sourceRepository.findByName(zoneState.getSourceName());
    if (zone.isEmpty()) {
      LOG.error("Referenced zone {} does not exist", zoneState.getName());
      return;
    }

    if (source.isEmpty()) {
      LOG.warn("No source configured for zone {}", zoneState.getName());
      return;
    }

    audioInterface.sync(zone.get(), source.get(), zoneState);
    LOG.debug("Synced {} with state {}", zone.get().getName(), zoneState);

    LOG.debug("Push zone update via MQTT");
    publishZoneToMqtt(zoneState);
  }

  private void publishZoneToMqtt(ZoneState zoneState) {
    for (var i = 1; i < 5; i++) {
      if (!mqttClient.isConnected()) {
        LOG.error("MQTT not connected, attempting to reconnect (retry #{})", i);
        try {
          mqttClient.reconnect();
          break;
        } catch (MqttException e) {
          LOG.error("Failed to reconnect... waiting a bit", e);
          try {
            Thread.sleep(5_000);
          } catch (InterruptedException ie) {
            LOG.error("Interrupted my sleep", e);
            break;
          }
        }
      }
    }

    try {
      String topic = topicBase + zoneState.getName();

      // Publish each value to its respective subtopic
      mqttClient.publish(
          topic + "/sourceName", new MqttMessage(zoneState.getSourceName().getBytes()));
      mqttClient.publish(
          topic + "/volume", new MqttMessage(String.valueOf(zoneState.getVolume()).getBytes()));
      mqttClient.publish(
          topic + "/muted", new MqttMessage(String.valueOf(zoneState.isMuted()).getBytes()));

      if (zoneState.getZoneDetails() != null) {
        mqttClient.publish(
            topic + "/description",
            new MqttMessage(zoneState.getZoneDetails().getDescription().getBytes()));
      }
    } catch (Exception e) {
      LOG.error("Failed to publish zone to MQTT", e);
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  public void resyncZoneState() {
    LOG.info("Zeroing out all zones on startup");
    ZoneState dummy = new ZoneState();
    dummy.setMuted(true);
    for (var zone : zoneRepository.findAll()) {
      dummy.setName(zone.getName());
      for (var source : sourceRepository.findAll()) {
        dummy.setSourceName(source.getName());
        syncZone(dummy);
      }
    }

    LOG.info("Re-syncing all previously known zone state.");
    for (var zoneState : zoneStateRepository.findAll()) {
      syncZone(zoneState);
    }
  }
}
