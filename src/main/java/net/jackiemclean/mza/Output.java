package net.jackiemclean.mza;

import jakarta.persistence.Embeddable;

@Embeddable
public class Output {

  private String name;

  public Output() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
