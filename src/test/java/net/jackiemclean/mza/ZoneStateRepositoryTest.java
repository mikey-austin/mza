package net.jackiemclean.mza;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:sqlite:/tmp/test_mza.db",
      "spring.datasource.initialization-mode=always",
      "spring.jpa.hibernate.ddl-auto=update"
    })
class ZoneStateRepositoryTest {

  @Autowired ZoneStateRepository zoneStateRepository;
  @Autowired ZoneRepository zoneRepository;
  @Autowired SourceRepository sourceRepository;

  @BeforeEach
  public void setup() {
    zoneStateRepository.deleteAll();
    ZoneState zs = new ZoneState();
    zs.setMuted(true);
    zs.setSourceName("pcm_source1");
    zs.setVolume(30);
    zs.setName("zone1");
    zoneStateRepository.save(zs);
  }

  @Test
  public void testSave() {
    var zoneState = zoneStateRepository.findById("zone1");
    assertTrue(zoneState.isPresent());
    assertEquals("pcm_source1", zoneState.get().getSourceName());
    assertEquals("zone1", zoneState.get().getName());

    zoneState.get().setVolume(0);
    zoneState.get().setMuted(false);
    zoneState.get().setSourceName("pcm_source2");
    zoneStateRepository.save(zoneState.get());

    var updatedZoneState = zoneStateRepository.findById("zone1");
    assertTrue(updatedZoneState.isPresent());
    assertEquals("pcm_source2", updatedZoneState.get().getSourceName());
    assertEquals("zone1", updatedZoneState.get().getName());
  }
}
