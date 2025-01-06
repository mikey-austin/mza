package net.jackiemclean.mza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
