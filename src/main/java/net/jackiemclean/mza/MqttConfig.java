package net.jackiemclean.mza;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttConfig {

  @Bean
  public MqttClient mqttClient(
      @Value("${mqtt.broker.url}") String broker, @Value("${mqtt.clientId}") String clientId)
      throws Exception {
    MqttClient client = new MqttClient(broker, clientId, new MemoryPersistence());

    MqttConnectOptions options = new MqttConnectOptions();
    options.setCleanSession(true);
    client.connect(options);

    return client;
  }
}
