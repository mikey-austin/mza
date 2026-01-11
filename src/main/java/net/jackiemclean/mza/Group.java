package net.jackiemclean.mza;

import java.time.Instant;
import java.util.Set;

public class Group {

  private String id;
  private String name;
  private String displayName;
  private Set<String> zones;
  private String description;
  private Instant createdAt;
  private Instant updatedAt;

  public Group() {
  }

  public static Group fromState(GroupState state) {
    Group group = new Group();
    group.setId(state.getId());
    group.setName(state.getName());
    group.setDisplayName(state.getDisplayName());
    group.setZones(state.getZones());
    group.setDescription(state.getDescription());
    group.setCreatedAt(state.getCreatedAt());
    group.setUpdatedAt(state.getUpdatedAt());
    return group;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public Set<String> getZones() {
    return zones;
  }

  public void setZones(Set<String> zones) {
    this.zones = zones;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
