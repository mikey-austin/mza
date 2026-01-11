package net.jackiemclean.mza;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Collection;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

  private static final Logger LOG = LoggerFactory.getLogger(GroupController.class);

  @Autowired
  private GroupService groupService;
  @Autowired
  private GroupStateRepository groupStateRepository;
  @Autowired
  private ZoneController zoneController;

  @GetMapping
  public Collection<GroupState> getAllGroups() {
    return groupStateRepository.findAll();
  }

  @GetMapping("/{name}")
  public GroupState getGroup(@PathVariable @NotBlank String name) {
    return groupStateRepository
        .findByName(name)
        .orElseThrow(() -> new RuntimeException("Group not found"));
  }

  @PostMapping
  public GroupState createGroup(@RequestBody CreateGroupRequest request) {
    return groupService.createGroup(
        request.getName(),
        request.getDisplayName(),
        request.getZones(),
        request.getDescription());
  }

  @PutMapping("/{name}")
  public GroupState updateGroup(
      @PathVariable @NotBlank String name, @RequestBody UpdateGroupRequest request) {
    return groupService.updateGroup(
        name, request.getDisplayName(), request.getZones(), request.getDescription());
  }

  @DeleteMapping("/{name}")
  public void deleteGroup(@PathVariable @NotBlank String name) {
    groupService.deleteGroup(name);
  }

  @PostMapping("/{name}/zones/{zoneName}")
  public GroupState addZoneToGroup(
      @PathVariable @NotBlank String name, @PathVariable @NotBlank String zoneName) {
    return groupService.addZoneToGroup(name, zoneName);
  }

  @DeleteMapping("/{name}/zones/{zoneName}")
  public GroupState removeZoneFromGroup(
      @PathVariable @NotBlank String name, @PathVariable @NotBlank String zoneName) {
    return groupService.removeZoneFromGroup(name, zoneName);
  }

  // Control endpoints - keep existing functionality
  @PatchMapping("/{name}/mute")
  public Collection<ZoneState> muteGroup(
      @PathVariable @NotBlank String name, @RequestParam boolean isMuted) {
    GroupState group = getGroup(name);
    return group.getZones().stream()
        .map(z -> zoneController.muteZone(z, isMuted))
        .collect(Collectors.toList());
  }

  @PatchMapping("/{name}/toggleMute")
  public Collection<ZoneState> toggleMuteGroup(@PathVariable @NotBlank String name) {
    GroupState group = getGroup(name);
    return group.getZones().stream()
        .map(z -> zoneController.toggleMuteZone(z))
        .collect(Collectors.toList());
  }

  @PatchMapping("/{name}/volume")
  public Collection<ZoneState> changeGroupVolume(
      @PathVariable @NotBlank String name, @RequestParam int volumePercent) {
    GroupState group = getGroup(name);
    return group.getZones().stream()
        .map(z -> zoneController.changeVolume(z, volumePercent))
        .collect(Collectors.toList());
  }

  @PatchMapping("/{name}/incrementVolume")
  public Collection<ZoneState> incrementGroupVolume(
      @PathVariable @NotBlank String name, @Min(-20) @Max(20) @RequestParam int increment) {
    // Snapcast-style: preserve relative volume differences
    GroupState group = getGroup(name);
    return group.getZones().stream()
        .map(z -> zoneController.incrementVolume(z, increment))
        .collect(Collectors.toList());
  }

  @PatchMapping("/{name}/source")
  public Collection<ZoneState> setGroupSource(
      @PathVariable @NotBlank String name, @RequestParam @NotBlank String sourceName) {
    GroupState group = getGroup(name);
    return group.getZones().stream()
        .map(z -> zoneController.changeSource(z, sourceName))
        .collect(Collectors.toList());
  }
}
