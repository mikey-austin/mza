package net.jackiemclean.mza;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/zones")
public class ZoneController {

  private final ZoneRepository zoneRepository;
  private final SourceRepository sourceRepository;

  @Autowired
  ZoneController(ZoneRepository zoneRepository, SourceRepository sourceRepository) {
    this.zoneRepository = zoneRepository;
    this.sourceRepository = sourceRepository;
  }

  @GetMapping
  public List<Zone> getAllZones() {
    return zoneRepository.findAll();
  }

  @GetMapping("/{name}")
  public Zone getZone(@PathVariable String name) {
    return zoneRepository.findById(name).orElseThrow(() -> new RuntimeException("Zone not found"));
  }

  @PostMapping
  public Zone createZone(@RequestBody Zone zone) {
    return zoneRepository.save(zone);
  }

  @PutMapping("/{name}")
  public Zone updateZone(@PathVariable String name, @RequestBody Zone updatedZone) {
    Zone zone =
        zoneRepository.findById(name).orElseThrow(() -> new RuntimeException("Zone not found"));
    zone.setVolume(updatedZone.getVolume());
    zone.setMuted(updatedZone.isMuted());
    zone.setLeftOutput(updatedZone.getLeftOutput());
    zone.setRightOutput(updatedZone.getRightOutput());
    zone.setSource(updatedZone.getSource());
    return zoneRepository.save(zone);
  }

  @DeleteMapping("/{name}")
  public void deleteZone(@PathVariable String name) {
    zoneRepository.deleteById(name);
  }
}
