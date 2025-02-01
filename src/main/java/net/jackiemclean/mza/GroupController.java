package net.jackiemclean.mza;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

  private static final Logger LOG = LoggerFactory.getLogger(GroupController.class);

  @Autowired private GroupConfig groupConfig;
  @Autowired private ZoneController zoneController;

  @GetMapping
  public Collection<Group> getAllGroups() {
    return groupConfig.getGroups();
  }

  @GetMapping("/{name}")
  public Optional<Group> getGroup(@PathVariable @NotBlank String name) {
    return groupConfig.getGroups().stream().filter(g -> name.equals(g.getName())).findFirst();
  }

  @PatchMapping("/{name}/mute")
  public Collection<ZoneState> muteGroup(
      @PathVariable @NotBlank String name, @RequestParam boolean isMuted) {
    var group = getGroup(name).orElseThrow(() -> new RuntimeException("no group found"));
    return group.getZones().stream()
        .map(z -> zoneController.muteZone(z, isMuted))
        .collect(Collectors.toList());
  }

  @PatchMapping("/{name}/toggleMute")
  public Collection<ZoneState> toggleMuteGroup(@PathVariable @NotBlank String name) {
    var group = getGroup(name).orElseThrow(() -> new RuntimeException("no group found"));
    return group.getZones().stream()
        .map(z -> zoneController.toggleMuteZone(z))
        .collect(Collectors.toList());
  }

  @PatchMapping("/{name}/volume")
  public Collection<ZoneState> changeGroupVolume(
      @PathVariable @NotBlank String name, @RequestParam int volumePercent) {
    var group = getGroup(name).orElseThrow(() -> new RuntimeException("no group found"));
    return group.getZones().stream()
        .map(z -> zoneController.changeVolume(z, volumePercent))
        .collect(Collectors.toList());
  }

  @PatchMapping("/{name}/incrementVolume")
  public Collection<ZoneState> incrementGroupVolume(
      @PathVariable String name, @Min(-20) @Max(20) @RequestParam int increment) {
    var group = getGroup(name).orElseThrow(() -> new RuntimeException("no group found"));
    return group.getZones().stream()
        .map(z -> zoneController.incrementVolume(z, increment))
        .collect(Collectors.toList());
  }

  @PatchMapping("/{name}/source")
  public Collection<ZoneState> setGroupSource(
      @PathVariable String name, @RequestParam @NotBlank String sourceName) {
    var group = getGroup(name).orElseThrow(() -> new RuntimeException("no group found"));
    return group.getZones().stream()
        .map(z -> zoneController.changeSource(z, sourceName))
        .collect(Collectors.toList());
  }
}
