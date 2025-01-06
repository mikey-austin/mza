package net.jackiemclean.mza;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/zones")
public class ZoneController {

  private static final Logger LOG = LoggerFactory.getLogger(ZoneController.class);

  @Autowired private ZoneStateRepository zoneStateRepository;
  @Autowired private SourceRepository sourceRepository;
  @Autowired private ZoneRepository zoneRepository;
  @Autowired private ZoneRouter zoneRouter;

  @GetMapping
  public List<ZoneState> getAllZones() {
    return zoneStateRepository.findAll();
  }

  @GetMapping("/{name}")
  public ZoneState getZone(@PathVariable String name) {
    return zoneStateRepository
        .findById(name)
        .or(() -> defaultState(name))
        .orElseThrow(() -> new RuntimeException("Zone not found"));
  }

  @PutMapping("/{name}/mute")
  public ZoneState muteZone(@PathVariable String name, @RequestParam boolean isMuted) {
    ZoneState zoneState =
        zoneStateRepository
            .findById(name)
            .or(() -> defaultState(name))
            .orElseThrow(() -> new RuntimeException("Zone not found"));
    zoneState.setMuted(isMuted);
    zoneRouter.syncZone(zoneState);
    return zoneStateRepository.save(zoneState);
  }

  @PutMapping("/{name}/toggleMute")
  public ZoneState toggleMuteZone(@PathVariable String name) {
    ZoneState zoneState =
        zoneStateRepository
            .findById(name)
            .or(() -> defaultState(name))
            .orElseThrow(() -> new RuntimeException("Zone not found"));
    zoneState.setMuted(!zoneState.isMuted());
    zoneRouter.syncZone(zoneState);
    return zoneStateRepository.save(zoneState);
  }

  @PutMapping("/{name}/volume")
  public ZoneState changeVolume(@PathVariable String name, @RequestParam int volumePercent) {
    ZoneState zoneState =
        zoneStateRepository
            .findById(name)
            .or(() -> defaultState(name))
            .orElseThrow(() -> new RuntimeException("Zone not found"));
    zoneState.setVolume(volumePercent);
    zoneRouter.syncZone(zoneState);
    return zoneStateRepository.save(zoneState);
  }

  @PutMapping("/{name}/source")
  public ZoneState changeSource(
      @PathVariable String name, @RequestParam @NotBlank String sourceName) {
    ZoneState zoneState =
        zoneStateRepository
            .findById(name)
            .or(() -> defaultState(name))
            .orElseThrow(() -> new RuntimeException("Zone not found"));
    Source source =
        sourceRepository
            .findByName(sourceName)
            .orElseThrow(() -> new RuntimeException("Source not found"));

    if (zoneState.getSourceName() != null && zoneState.getSourceName().equals(sourceName)) {
      LOG.warn("nothing to do, cannot configure with the same source");
      return zoneState;
    }

    // When changing source, we need to mute the existing connections.
    if (!zoneState.isMuted()) {
      LOG.info("Before source change, mute existing route");
      zoneState.setMuted(true);
      zoneRouter.syncZone(zoneState);
      zoneState.setMuted(false);
    }

    zoneState.setSourceName(source.getName());
    zoneRouter.syncZone(zoneState);
    return zoneStateRepository.save(zoneState);
  }

  private Optional<ZoneState> defaultState(String zoneName) {
    return zoneRepository
        .findByName(zoneName)
        .map(
            zone -> {
              LOG.info("creating default zone: {}", zoneName);
              var defaultState = new ZoneState();
              defaultState.setVolume(0);
              defaultState.setMuted(true);
              defaultState.setName(zoneName);
              return defaultState;
            });
  }
}
