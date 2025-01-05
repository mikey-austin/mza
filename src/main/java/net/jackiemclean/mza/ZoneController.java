package net.jackiemclean.mza;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/zones")
public class ZoneController {

  private final ZoneStateRepository zoneStateRepository;
  private final SourceRepository sourceRepository;

  @Autowired
  ZoneController(ZoneStateRepository zoneStateRepository, SourceRepository sourceRepository) {
    this.zoneStateRepository = zoneStateRepository;
    this.sourceRepository = sourceRepository;
  }

  @GetMapping
  public List<ZoneState> getAllZones() {
    return zoneStateRepository.findAll();
  }

  @GetMapping("/{name}")
  public ZoneState getZone(@PathVariable String name) {
    return zoneStateRepository
        .findById(name)
        .orElseThrow(() -> new RuntimeException("Zone not found"));
  }

  @PutMapping("/{name}/mute")
  public ZoneState muteZone(@PathVariable String name, @RequestParam boolean isMuted) {
    ZoneState zoneState =
        zoneStateRepository
            .findById(name)
            .orElseThrow(() -> new RuntimeException("Zone not found"));
    zoneState.setMuted(isMuted);
    return zoneStateRepository.save(zoneState);
  }

  @PutMapping("/{name}/toggleMute")
  public ZoneState toggleMuteZone(@PathVariable String name) {
    ZoneState zoneState =
        zoneStateRepository
            .findById(name)
            .orElseThrow(() -> new RuntimeException("Zone not found"));
    zoneState.setMuted(!zoneState.isMuted());
    return zoneStateRepository.save(zoneState);
  }

  @PutMapping("/{name}/volume")
  public ZoneState changeVolume(@PathVariable String name, @RequestParam int volumePercent) {
    ZoneState zoneState =
        zoneStateRepository
            .findById(name)
            .orElseThrow(() -> new RuntimeException("Zone not found"));
    zoneState.setVolume(volumePercent);
    return zoneStateRepository.save(zoneState);
  }

  @PutMapping("/{name}/source")
  public ZoneState changeVolume(
      @PathVariable String name, @RequestParam @NotBlank String sourceName) {
    ZoneState zoneState =
        zoneStateRepository
            .findById(name)
            .orElseThrow(() -> new RuntimeException("Zone not found"));
    Source source =
        sourceRepository
            .findByName(sourceName)
            .orElseThrow(() -> new RuntimeException("Source not found"));
    zoneState.setSourceName(source.getName());
    return zoneStateRepository.save(zoneState);
  }
}
