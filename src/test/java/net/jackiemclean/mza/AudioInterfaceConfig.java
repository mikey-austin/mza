package net.jackiemclean.mza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AudioInterfaceConfig {

  private static final Logger LOG = LoggerFactory.getLogger(AudioInterfaceConfig.class);

  @Bean
  @ConditionalOnProperty(name = "audio.interface.backend", havingValue = "DUMMY")
  public AudioInterface dummyInterface() {
    return new AudioInterface() {
      @Override
      public void link(Source source, Zone zone) {
        LOG.info("Linking {} -> {}", source, zone);
      }

      @Override
      public void sync(Zone zone, ZoneState zoneState) {
        LOG.info("Syncing {} with state {}", zone, zone);
      }
    };
  }
}
