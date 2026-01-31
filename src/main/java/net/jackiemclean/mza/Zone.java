package net.jackiemclean.mza;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
public class Zone {

  @Column(name = "zone_name")
  @Id
  private String name;

  @JsonProperty("left")
  @JsonAlias("leftOutput")
  @AttributeOverrides({
    @AttributeOverride(name = "name", column = @Column(name = "left_output_name")),
  })
  @Embedded
  private Output leftOutput;

  @JsonProperty("right")
  @JsonAlias("rightOutput")
  @AttributeOverrides({
    @AttributeOverride(name = "name", column = @Column(name = "right_output_name")),
  })
  @Embedded
  private Output rightOutput;

  private String description;

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

  public void setLeft(Output left) {
    this.leftOutput = left;
  }

  public Output getRightOutput() {
    return rightOutput;
  }

  public void setRightOutput(Output rightOutput) {
    this.rightOutput = rightOutput;
  }

  public void setRight(Output right) {
    this.rightOutput = right;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
