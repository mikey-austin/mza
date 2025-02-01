package net.jackiemclean.mza;

import java.util.Set;

public class Group {

  private String name;

  private Set<String> zones;

  public String getName() {
    return name;
  }

  public Set<String> getZones() {
    return zones;
  }

  public void setZones(Set<String> zones) {
    this.zones = zones;
  }

  public void setName(String name) {
    this.name = name;
  }
}
