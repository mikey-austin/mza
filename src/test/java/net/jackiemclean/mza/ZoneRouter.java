package net.jackiemclean.mza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ZoneRouter {

  private static final Logger LOG = LoggerFactory.getLogger(ZoneRouter.class);

  @Autowired ZoneStateRepository zoneStateRepository;
  @Autowired ZoneRepository zoneRepository;
  @Autowired SourceRepository sourceRepository;
  @Autowired AudioInterface audioInterface;

  public void refreshRoutes() {
    LOG.debug("starting full route refresh");

    for (var zoneState : zoneStateRepository.findAll()) {
      var zone = zoneRepository.findByName(zoneState.getSourceName());
      var source = sourceRepository.findByName(zoneState.getSourceName());
      if (zone.isEmpty()) {
        LOG.error("Referenced zone {} does not exist", zoneState.getName());
        continue;
      }

      if (source.isEmpty()) {
        LOG.error("Referenced source {} does not exist", zoneState.getSourceName());
        continue;
      }

      audioInterface.link(source.get(), zone.get());
      LOG.debug("Linked {} -> {}", zoneState.getSourceName(), zoneState.getName());

      audioInterface.sync(zone.get(), zoneState);
      LOG.debug("Synced {} with state {}", zone.get().getName(), zoneState);
    }
  }
}
