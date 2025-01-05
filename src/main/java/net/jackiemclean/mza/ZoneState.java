package net.jackiemclean.mza;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Entity
public class ZoneState {

  @Id
  @Column(name = "zone_name")
  private String name;

  private String sourceName;

  @Min(value = 0)
  @Max(value = 100)
  private int volume;

  private boolean isMuted;

  public ZoneState() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getVolume() {
    return volume;
  }

  public void setVolume(int volume) {
    this.volume = volume;
  }

  public boolean isMuted() {
    return isMuted;
  }

  public void setMuted(boolean muted) {
    isMuted = muted;
  }

  public String getSourceName() {
    return sourceName;
  }

  @Override
  public String toString() {
    return "ZoneState{"
        + "name='"
        + name
        + '\''
        + ", sourceName='"
        + sourceName
        + '\''
        + ", volume="
        + volume
        + ", isMuted="
        + isMuted
        + '}';
  }

  public void setSourceName(String sourceName) {
    this.sourceName = sourceName;
  }
}
