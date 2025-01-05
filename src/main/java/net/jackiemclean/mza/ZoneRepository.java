package net.jackiemclean.mza;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ZoneRepository {

  private final ZoneConfig zoneConfig;

  @Autowired
  public ZoneRepository(ZoneConfig zoneConfig) {
    this.zoneConfig = zoneConfig;
  }

  public Optional<Zone> findByName(String name) {
    return zoneConfig.getZones().stream().filter(zone -> zone.getName().equals(name)).findFirst();
  }
}
