package net.jackiemclean.mza;

import jakarta.persistence.*;

/**
 * This class represents an audio source.
 *
 * <p>Sources contain inputs and represent logical sources like TVs, PCMs, etc.
 */
@Entity
public class Source {

  @Column(name = "source_name")
  @Id
  private String name;

  @AttributeOverrides({
    @AttributeOverride(name = "name", column = @Column(name = "left_input_name")),
    @AttributeOverride(name = "device", column = @Column(name = "left_input_device"))
  })
  @Embedded
  private Input leftInput;

  @AttributeOverrides({
    @AttributeOverride(name = "name", column = @Column(name = "right_input_name")),
    @AttributeOverride(name = "device", column = @Column(name = "right_input_device"))
  })
  @Embedded
  private Input rightInput;

  public Source() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Input getLeftInput() {
    return leftInput;
  }

  public void setLeftInput(Input leftInput) {
    this.leftInput = leftInput;
  }

  public Input getRightInput() {
    return rightInput;
  }

  public void setRightInput(Input rightInput) {
    this.rightInput = rightInput;
  }
}
