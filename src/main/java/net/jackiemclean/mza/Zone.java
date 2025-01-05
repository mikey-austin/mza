package net.jackiemclean.mza;

import jakarta.persistence.*;

@Entity
public class Zone {

  @Column(name = "zone_name")
  @Id
  private String name;

  @AttributeOverrides({
    @AttributeOverride(name = "name", column = @Column(name = "left_output_name")),
    @AttributeOverride(name = "device", column = @Column(name = "left_output_device"))
  })
  @Embedded
  private Output leftOutput;

  @AttributeOverrides({
    @AttributeOverride(name = "name", column = @Column(name = "right_output_name")),
    @AttributeOverride(name = "device", column = @Column(name = "right_output_device"))
  })
  @Embedded
  private Output rightOutput;

  public Zone() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Output getLeftOutput() {
    return leftOutput;
  }

  public void setLeftOutput(Output leftOutput) {
    this.leftOutput = leftOutput;
  }

  public Output getRightOutput() {
    return rightOutput;
  }

  public void setRightOutput(Output rightOutput) {
    this.rightOutput = rightOutput;
  }
}
