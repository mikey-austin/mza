package net.jackiemclean.mza;

import jakarta.persistence.Embeddable;

@Embeddable
public class Input {
  private String name;

  public Input() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
