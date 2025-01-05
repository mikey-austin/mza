package net.jackiemclean.mza;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ZoneRepositoryTest {

  @Autowired ZoneRepository zoneRepository;

  @Test
  public void testOkFetch() {
    var zone = zoneRepository.findByName("zone1");
    assertTrue(zone.isPresent());
    assertEquals("zone1", zone.get().getName());
  }
}
