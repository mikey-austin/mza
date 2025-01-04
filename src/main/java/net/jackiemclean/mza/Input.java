package net.jackiemclean.mza;

import jakarta.persistence.Embeddable;

@Embeddable
public class Input {
  private String name;
  private String device;

  public Input() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDevice() {
    return device;
  }

  public void setDevice(String device) {
    this.device = device;
  }
}
